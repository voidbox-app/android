package org.cryptomator.presentation.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.LruCache
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.util.crypto.CredentialCryptor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Thumbnails of vault images. Decoded bitmaps live in memory while vaults are open; on disk they
 * are stored only encrypted with the app's keystore key, in the app's own cache directory, so no
 * plaintext thumbnail ever exists outside this process.
 */
@Singleton
class ThumbnailCache @Inject constructor(private val context: Context) {

	private val memory = object : LruCache<String, Bitmap>(((Runtime.getRuntime().maxMemory() / 8).toInt()).coerceAtLeast(4 * 1024 * 1024)) {
		override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
	}
	private val directory = File(context.cacheDir, "thumbnails")
	private val cryptor by lazy { CredentialCryptor.getInstance(context) }

	fun key(file: CloudFileModel): String {
		val node = file.toCloudNode()
		val owner = node.cloud?.id()?.toString() ?: node.cloud?.type()?.name ?: ""
		val digest = MessageDigest.getInstance("SHA-256").digest("$owner|${file.path}|${file.modified?.time}|${file.size}".toByteArray())
		return digest.joinToString("") { "%02x".format(it) }
	}

	/** Memory only; safe on the main thread. */
	fun peek(key: String): Bitmap? = memory.get(key)

	/** Memory, then the encrypted copy on disk. Call off the main thread. */
	fun get(key: String): Bitmap? {
		memory.get(key)?.let { return it }
		val stored = File(directory, key)
		if (!stored.exists()) {
			return null
		}
		return try {
			val jpeg = cryptor.decrypt(stored.readBytes())
			BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.also { memory.put(key, it) }
		} catch (e: Exception) {
			Timber.tag("Thumbnails").w(e, "Dropping unreadable thumbnail")
			stored.delete()
			null
		}
	}

	/** Decodes a downloaded image into a thumbnail and stores it; null when the data is not an image. Call off the main thread. */
	fun decodeAndStore(key: String, image: ByteArray): Bitmap? {
		val thumbnail = decode(image) ?: return null
		memory.put(key, thumbnail)
		try {
			directory.mkdirs()
			val jpeg = ByteArrayOutputStream().also { thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
			File(directory, key).writeBytes(cryptor.encrypt(jpeg))
			trimDisk()
		} catch (e: Exception) {
			Timber.tag("Thumbnails").w(e, "Could not store thumbnail")
		}
		return thumbnail
	}

	/** Forgets the decoded bitmaps; the encrypted files stay for the next unlock. */
	fun clearMemory() {
		memory.evictAll()
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
		val files = directory.listFiles()?.sortedBy { it.lastModified() } ?: return
		var total = files.sumOf { it.length() }
		for (file in files) {
			if (total <= DISK_LIMIT_BYTES) break
			total -= file.length()
			file.delete()
		}
	}

	companion object {

		const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
		private const val SIZE_PX = 192
		private const val DISK_LIMIT_BYTES = 64L * 1024L * 1024L
	}
}
