package org.cryptomator.presentation.util

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import org.cryptomator.data.repository.DispatchingCloudContentRepository
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.RandomAccessContent
import org.cryptomator.presentation.model.CloudFileModel
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Ranged access to vault media for the player and the thumbnail extractor. */
@Singleton
class VaultMedia @Inject constructor(private val cloudContentRepository: DispatchingCloudContentRepository) {

	/** Call off the main thread. */
	@Throws(BackendException::class)
	fun open(file: CloudFileModel): RandomAccessContent {
		return cloudContentRepository.openRandomAccess(file.toCloudNode())
	}

	/** Fetches the header and one chunk. Call off the main thread. */
	fun supportsStreaming(file: CloudFileModel): Boolean {
		return try {
			open(file).use { content -> content.openStream(0, 1).use { it.read() >= 0 } }
		} catch (e: Exception) {
			Timber.tag("VaultMedia").i(e, "%s cannot be streamed, downloading instead", file.name)
			false
		}
	}

	/** A frame from about 12 % in, skipping blank frames; null when the format is not understood. */
	fun frame(source: RandomAccessMediaDataSource): Bitmap? {
		val retriever = MediaMetadataRetriever()
		try {
			retriever.setDataSource(source)
			val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
			var frame: Bitmap? = null
			for (fraction in FRAME_FRACTIONS) {
				val candidate = retriever.frameAt(frameTimeUs(durationMs, fraction)) ?: break
				frame?.recycle()
				frame = candidate
				if (!candidate.isBlank()) {
					break
				}
			}
			return frame
		} finally {
			retriever.release()
		}
	}

	// phone clips often start dark or smeared; very short ones have no room to skip
	private fun frameTimeUs(durationMs: Long, fraction: Double): Long {
		val atMs = when {
			durationMs < SHORT_CLIP_MS -> durationMs / 3
			durationMs * fraction < MIN_FRAME_AT_MS -> MIN_FRAME_AT_MS
			else -> (durationMs * fraction).toLong()
		}
		return atMs * 1000
	}

	private fun MediaMetadataRetriever.frameAt(timeUs: Long): Bitmap? {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
			getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_PX, FRAME_PX)
		} else {
			getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
		}
	}

	// black or a single colour, judged on an 8x8 downscale
	private fun Bitmap.isBlank(): Boolean {
		val small = Bitmap.createScaledBitmap(this, BLANK_GRID, BLANK_GRID, true)
		val pixels = IntArray(BLANK_GRID * BLANK_GRID)
		small.getPixels(pixels, 0, BLANK_GRID, 0, 0, BLANK_GRID, BLANK_GRID)
		if (small !== this) {
			small.recycle()
		}
		val luma = pixels.map { (Color.red(it) * 299 + Color.green(it) * 587 + Color.blue(it) * 114) / 1000 }
		return luma.max() < BLACK_LUMA || luma.max() - luma.min() < FLAT_LUMA_RANGE
	}

	companion object {

		private val FRAME_FRACTIONS = listOf(0.12, 0.25, 0.5)
		private const val SHORT_CLIP_MS = 3_000L
		private const val MIN_FRAME_AT_MS = 1_500L
		private const val FRAME_PX = 384
		private const val BLANK_GRID = 8
		private const val BLACK_LUMA = 24
		private const val FLAT_LUMA_RANGE = 12
	}
}
