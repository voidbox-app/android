package org.cryptomator.data.util

import android.content.Context
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Vault files kept offline: the ciphertext as the cloud holds it, under `files/offline/<vaultId>/`.
 * In files, not cache, so clearing the cache keeps them.
 *
 * A copy is named after the hash of its ciphertext path and sits next to a `.meta` file with the
 * path, size and date it was made from, so the index can be rebuilt from disk and a copy is stale
 * as soon as the cloud file's size or date differs. A copy without a `.meta` is from before the
 * metadata existed and is dropped on load. Copies are indexed per vault: a vault added again under
 * a new id can leave the same paths in two folders, and each belongs to its own vault.
 *
 * The index is loaded in the background; the lookups wait for it once and then stay in memory.
 */
class OfflineCopies internal constructor(private val directory: File?) {

	private data class Key(val vaultId: Long, val path: String)

	private class Copy(val file: File, val meta: File, val size: Long?, val modified: Long?) {

		/** A lookup that does not know the size or the date, as after a rename, does not mean the file changed. */
		fun matches(ciphertext: CloudFile): Boolean {
			val askedSize = ciphertext.size
			val askedModified = ciphertext.modified?.time
			return (askedSize == null || askedSize == size) && (askedModified == null || askedModified == modified)
		}

		/** A lookup with an older date than the copy comes from a stale listing, not from a changed file. */
		fun isNewerThan(ciphertext: CloudFile): Boolean {
			val asked = ciphertext.modified?.time ?: return false
			return modified != null && modified > asked
		}

		fun delete() {
			file.delete()
			meta.delete()
		}
	}

	class VaultUsage(val vaultId: Long, val bytes: Long, val files: Int)

	private val index = ConcurrentHashMap<Key, Copy>()
	private val loaded = CountDownLatch(1)
	private val commit = Any()
	private val background: Executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "offline-copies").also { it.isDaemon = true } }

	init {
		if (directory == null) {
			loaded.countDown()
		} else {
			background.execute {
				try {
					load(directory)
				} finally {
					loaded.countDown()
				}
			}
		}
	}

	private fun load(directory: File) {
		directory.mkdirs()
		directory.listFiles()?.forEach { vault ->
			val vaultId = vault.name.toLongOrNull() ?: return@forEach
			vault.listFiles()?.forEach { file ->
				when {
					file.name.endsWith(PART) -> file.delete()
					file.name.endsWith(META) -> if (!File(vault, file.name.removeSuffix(META)).exists()) file.delete()
					else -> {
						val meta = File(vault, file.name + META)
						val copy = readMeta(file, meta)
						if (copy == null) {
							file.delete()
							meta.delete()
						} else {
							index[Key(vaultId, copy.first)] = copy.second
						}
					}
				}
			}
		}
	}

	private fun readMeta(file: File, meta: File): Pair<String, Copy>? {
		if (!meta.exists()) return null
		val lines = try {
			meta.readLines()
		} catch (e: IOException) {
			return null
		}
		if (lines.size < 3 || lines[0].isEmpty()) return null
		return lines[0] to Copy(file, meta, lines[1].toLongOrNull(), lines[2].toLongOrNull())
	}

	private fun ready() {
		loaded.await()
	}

	/** The copies of one vault. */
	fun vault(vaultId: Long): VaultCopies = VaultCopies(vaultId)

	inner class VaultCopies internal constructor(private val vaultId: Long) {

		/** Memory only, safe on the main thread. A copy of an older version of the file is dropped. */
		fun isKept(ciphertext: CloudFile): Boolean = current(ciphertext) { copy -> background.execute { copy.delete() } } != null

		/** The copy to read, checked on disk; null when there is none or the cloud file has changed. */
		fun find(ciphertext: CloudFile): File? {
			val copy = current(ciphertext) { it.delete() } ?: return null
			if (!copy.file.exists() || (copy.size != null && copy.file.length() != copy.size)) {
				index.remove(key(ciphertext), copy)
				copy.delete()
				return null
			}
			return copy.file
		}

		private fun current(ciphertext: CloudFile, evict: (Copy) -> Unit): Copy? {
			ready()
			val key = key(ciphertext)
			val copy = index[key] ?: return null
			if (copy.matches(ciphertext)) {
				return copy
			}
			if (!copy.isNewerThan(ciphertext) && index.remove(key, copy)) {
				evict(copy)
			}
			return null
		}

		/** [write] fills a temporary file that becomes the copy only when complete. */
		@Throws(IOException::class)
		fun store(ciphertext: CloudFile, write: (File) -> Unit): File {
			val folder = folder() ?: throw IOException("There is no storage for offline copies")
			ready()
			folder.mkdirs()
			val name = name(ciphertext.path)
			val part = File.createTempFile(name, PART, folder)
			try {
				write(part)
				synchronized(commit) {
					val copy = File(folder, name)
					val meta = File(folder, name + META)
					val size = ciphertext.size ?: part.length()
					val modified = ciphertext.modified?.time
					writeMeta(meta, ciphertext.path, size, modified)
					if (!part.renameTo(copy)) {
						throw IOException("Could not keep ${ciphertext.path}")
					}
					index[key(ciphertext)] = Copy(copy, meta, size, modified)
					return copy
				}
			} finally {
				part.delete()
			}
		}

		fun remove(ciphertext: CloudFile) {
			ready()
			index.remove(key(ciphertext))?.delete()
		}

		/** Drops the copies of every file below the folder, for a folder that is deleted. */
		fun removeBelow(folder: CloudFolder) {
			ready()
			val prefix = folder.path + "/"
			index.filterKeys { it.vaultId == vaultId && it.path.startsWith(prefix) }.forEach { (key, copy) ->
				if (index.remove(key, copy)) {
					copy.delete()
				}
			}
		}

		/**
		 * The file moved in the cloud with its content unchanged: the copy follows it. A move may
		 * report the target without size or date (long names in vault format 7); the copy keeps its own.
		 */
		fun move(from: CloudFile, to: CloudFile) {
			ready()
			synchronized(commit) {
				val copy = index.remove(key(from)) ?: return
				val folder = copy.file.parentFile
				val name = name(to.path)
				val moved = File(folder, name)
				val meta = File(folder, name + META)
				val size = to.size ?: copy.size
				val modified = to.modified?.time ?: copy.modified
				if (copy.file.renameTo(moved)) {
					copy.meta.delete()
					try {
						writeMeta(meta, to.path, size, modified)
						index[key(to)] = Copy(moved, meta, size, modified)
					} catch (e: IOException) {
						moved.delete()
						meta.delete()
					}
				} else {
					copy.delete()
				}
			}
		}

		/**
		 * The file was written anew: when it was kept, the copy is replaced with the new [ciphertext],
		 * otherwise nothing happens. A copy that cannot be replaced is dropped rather than left stale.
		 */
		fun replace(previous: CloudFile, current: CloudFile, ciphertext: File) {
			ready()
			index[key(previous)] ?: return
			try {
				store(current) { part -> ciphertext.copyTo(part, overwrite = true) }
			} catch (e: IOException) {
				remove(previous)
				throw e
			}
		}

		private fun key(ciphertext: CloudFile) = Key(vaultId, ciphertext.path)

		private fun folder(): File? = directory?.let { File(it, vaultId.toString()) }
	}

	private fun writeMeta(meta: File, path: String, size: Long?, modified: Long?) {
		meta.writeText("$path\n${size ?: ""}\n${modified ?: ""}\n")
	}

	fun deleteVault(vaultId: Long) {
		ready()
		index.keys.removeIf { it.vaultId == vaultId }
		directory?.let { File(it, vaultId.toString()).deleteRecursively() }
	}

	fun deleteAll() {
		ready()
		index.clear()
		directory?.listFiles()?.forEach { it.deleteRecursively() }
	}

	/** Bytes and files per vault, from the index alone: safe on the main thread. */
	fun usage(): List<VaultUsage> {
		ready()
		return index.entries
			.groupBy({ it.key.vaultId }, { it.value })
			.map { (vaultId, copies) -> VaultUsage(vaultId, copies.sumOf { it.size ?: 0L }, copies.size) }
	}

	private fun name(path: String): String {
		val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
		val hex = CharArray(digest.size * 2)
		digest.forEachIndexed { i, byte ->
			hex[i * 2] = HEX[(byte.toInt() shr 4) and 0xf]
			hex[i * 2 + 1] = HEX[byte.toInt() and 0xf]
		}
		return String(hex)
	}

	companion object {

		private const val PART = ".part"
		private const val META = ".meta"
		private val HEX = "0123456789abcdef".toCharArray()

		@Volatile
		private var instance: OfflineCopies? = null

		// unit tests pass a context without app storage
		private val DISABLED = OfflineCopies(null)

		fun of(context: Context): OfflineCopies {
			instance?.let { return it }
			val filesDir = (context.applicationContext ?: context).filesDir ?: return DISABLED
			return synchronized(this) {
				instance ?: OfflineCopies(File(filesDir, "offline")).also { instance = it }
			}
		}
	}
}
