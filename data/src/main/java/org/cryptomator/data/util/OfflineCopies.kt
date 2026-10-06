package org.cryptomator.data.util

import android.content.Context
import org.cryptomator.domain.CloudFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Vault files kept offline: the ciphertext as the cloud holds it, under `files/offline/<vaultId>/`,
 * keyed by path, size and date. In files, not cache, so clearing the cache keeps them.
 */
class OfflineCopies private constructor(private val directory: File?) {

	private val index = ConcurrentHashMap<String, File>()

	init {
		directory?.mkdirs()
		directory?.listFiles()?.forEach { vault -> vault.listFiles()?.forEach { copy -> if (!copy.name.endsWith(PART)) index[copy.name] = copy } }
	}

	/** Null when there is no copy or the cloud file has changed. */
	fun find(ciphertext: CloudFile): File? {
		val copy = index[key(ciphertext)] ?: return null
		if (!copy.exists() || (ciphertext.size != null && copy.length() != ciphertext.size)) {
			index.remove(key(ciphertext))
			copy.delete()
			return null
		}
		return copy
	}

	/** [write] fills a temporary file that becomes the copy only when complete. */
	@Throws(IOException::class)
	fun store(vaultId: Long, ciphertext: CloudFile, write: (File) -> Unit): File {
		directory ?: throw IOException("There is no storage for offline copies")
		val key = key(ciphertext)
		val folder = File(directory, vaultId.toString()).also { it.mkdirs() }
		val copy = File(folder, key)
		val part = File(folder, key + PART)
		try {
			write(part)
			if (!part.renameTo(copy)) {
				throw IOException("Could not keep $copy")
			}
			index[key] = copy
			return copy
		} finally {
			part.delete()
		}
	}

	fun remove(ciphertext: CloudFile) {
		index.remove(key(ciphertext))?.delete()
	}

	fun deleteVault(vaultId: Long) {
		index.values.removeIf { it.parentFile?.name == vaultId.toString() }
		directory?.let { File(it, vaultId.toString()).deleteRecursively() }
	}

	private fun key(ciphertext: CloudFile): String {
		val digest = MessageDigest.getInstance("SHA-256").digest("${ciphertext.path}|${ciphertext.size}|${ciphertext.modified?.time}".toByteArray())
		return digest.joinToString("") { "%02x".format(it) }
	}

	companion object {

		private const val PART = ".part"

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
