package org.cryptomator.presentation.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import org.cryptomator.data.repository.DispatchingCloudContentRepository
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.RandomAccessContent
import org.cryptomator.presentation.model.CloudFileModel
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Vault media read in pieces straight from the cloud: what the player and the thumbnail
 * extractor need, and nothing more, is fetched and decrypted in memory.
 */
@Singleton
class VaultMedia @Inject constructor(private val cloudContentRepository: DispatchingCloudContentRepository) {

	/** Opens [file] for ranged reads. Nothing is fetched until a range is asked for. Call off the main thread. */
	@Throws(BackendException::class)
	fun open(file: CloudFileModel): RandomAccessContent {
		return cloudContentRepository.openRandomAccess(file.toCloudNode())
	}

	/**
	 * Whether [file] can be played without downloading it: the cloud must honour ranged reads and
	 * the first piece must decrypt. Costs the file header and one chunk. Call off the main thread.
	 */
	fun supportsStreaming(file: CloudFileModel): Boolean {
		return try {
			open(file).use { content -> content.openStream(0, 1).use { it.read() >= 0 } }
		} catch (e: Exception) {
			Timber.tag("VaultMedia").i(e, "%s cannot be streamed, downloading instead", file.name)
			false
		}
	}

	/**
	 * A frame from a little way into the video, where the picture has usually settled, or the first
	 * keyframe of a very short clip. Null when the extractor does not understand the data.
	 */
	fun frame(source: RandomAccessMediaDataSource): Bitmap? {
		val retriever = MediaMetadataRetriever()
		try {
			retriever.setDataSource(source)
			val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
			val atUs = if (durationMs > FRAME_AT_MS * 3) FRAME_AT_MS * 1000 else 0L
			return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
				retriever.getScaledFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_PX, FRAME_PX)
			} else {
				retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
			}
		} finally {
			retriever.release()
		}
	}

	companion object {

		private const val FRAME_AT_MS = 1_000L
		private const val FRAME_PX = 384
	}
}
