package org.cryptomator.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.Cryptors
import org.cryptomator.domain.Vault
import org.cryptomator.presentation.model.CloudFileModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Thumbnails of vault images. Decoded bitmaps stay in memory while their vault is unlocked. On
 * disk, inside the app's cache directory, they are stored encrypted with a key derived from the
 * vault's own cryptor, so they can only be read while that vault is unlocked: locking the vault
 * makes the stored thumbnails as unreadable as the vault itself.
 */
@Singleton
class ThumbnailCache @Inject constructor(private val context: Context, private val cryptors: Cryptors) {

	private val memory = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 8).toInt()).coerceAtLeast(4 * 1024 * 1024)) {
		override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
	}
	private val directory = File(context.cacheDir, "thumbnails")
	private val knownVaults = ConcurrentHashMap<Long, Vault>()
	private val random = SecureRandom()

	/** The vault a file belongs to, or null for files outside a vault (those get no thumbnails). */
	fun vaultOf(file: CloudFileModel): Vault? = (file.toCloudNode().cloud as? CryptoCloud)?.vault

	/** Memory only; safe on the main thread. */
	fun peek(file: CloudFileModel): Bitmap? {
		val vault = vaultOf(file) ?: return null
		return memory.get(key(vault, file))
	}

	/** Memory, then the encrypted copy on disk, readable only while the vault is unlocked. Call off the main thread. */
	fun get(file: CloudFileModel): Bitmap? {
		val vault = vaultOf(file) ?: return null
		val key = key(vault, file)
		memory.get(key)?.let { return it }
		val stored = File(vaultDirectory(vault), hash(file))
		if (!stored.exists()) {
			return null
		}
		val vaultKey = vaultKey(vault) ?: return null
		return try {
			val jpeg = decrypt(vaultKey, stored.readBytes())
			BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.also { memory.put(key, it) }
		} catch (e: Exception) {
			Timber.tag("Thumbnails").w(e, "Dropping unreadable thumbnail")
			stored.delete()
			null
		}
	}

	/** Decodes a downloaded image into a thumbnail and stores it; null when the data is not an image. Call off the main thread. */
	fun decodeAndStore(file: CloudFileModel, image: ByteArray): Bitmap? {
		val vault = vaultOf(file) ?: return null
		val thumbnail = decode(image) ?: return null
		memory.put(key(vault, file), thumbnail)
		val vaultKey = vaultKey(vault) ?: return thumbnail
		try {
			val folder = vaultDirectory(vault).also { it.mkdirs() }
			val jpeg = ByteArrayOutputStream().also { thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
			File(folder, hash(file)).writeBytes(encrypt(vaultKey, jpeg))
			trimDisk()
		} catch (e: Exception) {
			Timber.tag("Thumbnails").w(e, "Could not store thumbnail")
		}
		return thumbnail
	}

	/** Forgets every decoded bitmap; the encrypted files stay for the next unlock. */
	fun clearMemory() {
		memory.evictAll()
	}

	/** Drops the decoded bitmaps of vaults that are no longer unlocked. */
	fun evictLockedVaults() {
		val locked = knownVaults.values.filter { vaultKey(it) == null }.map { it.id }
		if (locked.isEmpty()) {
			return
		}
		memory.snapshot().keys.filter { key -> locked.any { key.startsWith("$it/") } }.forEach { memory.remove(it) }
		locked.forEach { knownVaults.remove(it) }
	}

	/** Removes everything stored for a vault, e.g. when it is removed from the list. */
	fun deleteVault(vaultId: Long) {
		memory.snapshot().keys.filter { it.startsWith("$vaultId/") }.forEach { memory.remove(it) }
		knownVaults.remove(vaultId)
		File(directory, vaultId.toString()).deleteRecursively()
	}

	private fun key(vault: Vault, file: CloudFileModel): String {
		knownVaults[vault.id] = vault
		return "${vault.id}/${hash(file)}"
	}

	private fun hash(file: CloudFileModel): String {
		val digest = MessageDigest.getInstance("SHA-256").digest("${file.path}|${file.modified?.time}|${file.size}".toByteArray())
		return digest.joinToString("") { "%02x".format(it) }
	}

	private fun vaultDirectory(vault: Vault): File = File(directory, vault.id.toString())

	// The key is a function of the vault's masterkey: hashDirectoryId is AES-SIV under that key, so
	// nothing on disk can be opened without unlocking the vault. Nothing is kept once the cryptor is gone.
	private fun vaultKey(vault: Vault): SecretKey? {
		return try {
			val material = cryptors[vault].get().fileNameCryptor().hashDirectoryId(KEY_LABEL)
			SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material.toByteArray()), "AES")
		} catch (e: Exception) {
			null
		}
	}

	private fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
		val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
		val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
		return iv + cipher.doFinal(plain)
	}

	private fun decrypt(key: SecretKey, stored: ByteArray): ByteArray {
		val cipher = Cipher.getInstance(CIPHER).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES)) }
		return cipher.doFinal(stored, IV_BYTES, stored.size - IV_BYTES)
	}

	private fun decode(image: ByteArray): Bitmap? {
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
		if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
			return null
		}
		var sample = 1
		while (bounds.outWidth / (sample * 2) >= SIZE_PX && bounds.outHeight / (sample * 2) >= SIZE_PX) {
			sample *= 2
		}
		val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
		return rotateToExif(bitmap, image)
	}

	private fun rotateToExif(bitmap: Bitmap, image: ByteArray): Bitmap {
		val degrees = try {
			when (ExifInterface(ByteArrayInputStream(image)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
				ExifInterface.ORIENTATION_ROTATE_90 -> 90f
				ExifInterface.ORIENTATION_ROTATE_180 -> 180f
				ExifInterface.ORIENTATION_ROTATE_270 -> 270f
				else -> 0f
			}
		} catch (e: Exception) {
			0f
		}
		if (degrees == 0f) {
			return bitmap
		}
		val matrix = Matrix().apply { postRotate(degrees) }
		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
	}

	private fun trimDisk() {
		val files = directory.walkTopDown().filter { it.isFile }.sortedBy { it.lastModified() }.toList()
		var total = files.sumOf { it.length() }
		for (file in files) {
			if (total <= DISK_LIMIT_BYTES) break
			total -= file.length()
			file.delete()
		}
	}

	companion object {

		const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
		private const val KEY_LABEL = "latch-thumbnails-v1"
		private const val CIPHER = "AES/GCM/NoPadding"
		private const val IV_BYTES = 12
		private const val TAG_BITS = 128
		private const val SIZE_PX = 192
		private const val DISK_LIMIT_BYTES = 64L * 1024L * 1024L
	}
}
