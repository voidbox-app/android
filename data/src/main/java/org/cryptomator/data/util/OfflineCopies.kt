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
 * metadata existed and is dropped on load.
 *
 * The index is loaded in the background; the lookups wait for it once and then stay in memory.
 */
class OfflineCopies internal constructor(private val directory: File?) {

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

	private val index = ConcurrentHashMap<String, Copy>()
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
							index[copy.first] = copy.second
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

	/** Memory only, safe on the main thread. A copy of an older version of the file is dropped. */
	fun isKept(ciphertext: CloudFile): Boolean = current(ciphertext) { copy -> background.execute { copy.delete() } } != null

	/** The copy to read, checked on disk; null when there is none or the cloud file has changed. */
	fun find(ciphertext: CloudFile): File? {
		val copy = current(ciphertext) { it.delete() } ?: return null
		if (!copy.file.exists() || (copy.size != null && copy.file.length() != copy.size)) {
			index.remove(ciphertext.path, copy)
			copy.delete()
			return null
		}
		return copy.file
	}

	private fun current(ciphertext: CloudFile, evict: (Copy) -> Unit): Copy? {
		ready()
		val copy = index[ciphertext.path] ?: return null
		if (copy.matches(ciphertext)) {
			return copy
		}
		if (!copy.isNewerThan(ciphertext) && index.remove(ciphertext.path, copy)) {
			evict(copy)
		}
		return null
	}

	/** [write] fills a temporary file that becomes the copy only when complete. */
	@Throws(IOException::class)
	fun store(vaultId: Long, ciphertext: CloudFile, write: (File) -> Unit): File {
		directory ?: throw IOException("There is no storage for offline copies")
		ready()
		return store(File(directory, vaultId.toString()), ciphertext, write)
	}

	private fun store(folder: File, ciphertext: CloudFile, write: (File) -> Unit): File {
		folder.mkdirs()
		val name = name(ciphertext.path)
		val part = File.createTempFile(name, PART, folder)
		try {
			write(part)
			synchronized(commit) {
				val copy = File(folder, name)
				val meta = File(folder, name + META)
				writeMeta(meta, ciphertext)
				if (!part.renameTo(copy)) {
					throw IOException("Could not keep ${ciphertext.path}")
				}
				index[ciphertext.path] = Copy(copy, meta, ciphertext.size, ciphertext.modified?.time)
				return copy
			}
		} finally {
			part.delete()
		}
	}

	private fun writeMeta(meta: File, ciphertext: CloudFile) {
		writeMeta(meta, ciphertext.path, ciphertext.size, ciphertext.modified?.time)
	}

	private fun writeMeta(meta: File, path: String, size: Long?, modified: Long?) {
		meta.writeText("$path\n${size ?: ""}\n${modified ?: ""}\n")
	}

	fun remove(ciphertext: CloudFile) {
		ready()
		index.remove(ciphertext.path)?.delete()
	}

	/** Drops the copies of every file below the folder, for a folder that is deleted. */
	fun removeBelow(folder: CloudFolder) {
		ready()
		val prefix = folder.path + "/"
		index.filterKeys { it.startsWith(prefix) }.forEach { (path, copy) ->
			if (index.remove(path, copy)) {
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
			val copy = index.remove(from.path) ?: return
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
					index[to.path] = Copy(moved, meta, size, modified)
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
		val copy = index[previous.path] ?: return
		try {
			store(copy.file.parentFile, current) { part -> ciphertext.copyTo(part, overwrite = true) }
		} catch (e: IOException) {
			remove(previous)
			throw e
		}
	}

	fun deleteVault(vaultId: Long) {
		ready()
		index.values.removeIf { it.file.parentFile?.name == vaultId.toString() }
		directory?.let { File(it, vaultId.toString()).deleteRecursively() }
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
