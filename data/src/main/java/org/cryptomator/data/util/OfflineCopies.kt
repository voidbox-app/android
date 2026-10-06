package org.cryptomator.data.util

import android.content.Context
import org.cryptomator.domain.CloudFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Vault files the user asked to keep on the device: the ciphertext exactly as the cloud holds it,
 * under `files/offline/<vaultId>/`, so it is as unreadable without the vault's password as the
 * cloud copy. A copy is keyed by the ciphertext's path, size and modification date and is simply
 * missed once the file changes in the cloud. Lives in the app's files, not its cache, so neither
 * Android nor "Clear cache" throws it away.
 */
class OfflineCopies private constructor(private val directory: File?) {

	// key -> the stored copy, whichever vault it belongs to
	private val index = ConcurrentHashMap<String, File>()

	init {
		directory?.mkdirs()
		directory?.listFiles()?.forEach { vault -> vault.listFiles()?.forEach { copy -> if (!copy.name.endsWith(PART)) index[copy.name] = copy } }
	}

	/** The stored copy of [ciphertext], or null when there is none or it no longer matches the cloud file. */
	fun find(ciphertext: CloudFile): File? {
		val copy = index[key(ciphertext)] ?: return null
		if (!copy.exists() || (ciphertext.size != null && copy.length() != ciphertext.size)) {
			index.remove(key(ciphertext))
			copy.delete()
			return null
		}
		return copy
	}

	/** Lets [write] fill a temporary file and keeps it as the copy of [ciphertext] once it is complete. */
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

		// a bare context without app storage (unit tests) keeps nothing offline
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
