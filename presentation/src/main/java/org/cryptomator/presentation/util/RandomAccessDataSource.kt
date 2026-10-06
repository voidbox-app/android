package org.cryptomator.presentation.util

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.RandomAccessContent
import java.io.IOException
import java.io.InputStream

/**
 * Lets Media3 play a vault file straight from the cloud: each range the player asks for is
 * fetched and decrypted on the spot, so nothing decrypted touches the disk and playback starts
 * before the file is downloaded.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RandomAccessDataSource(private val content: RandomAccessContent) : BaseDataSource(true) {

	private var uri: Uri? = null
	private var stream: InputStream? = null
	private var remaining = 0L

	@Throws(IOException::class)
	override fun open(dataSpec: DataSpec): Long {
		uri = dataSpec.uri
		transferInitializing(dataSpec)
		if (dataSpec.position > content.size) {
			throw IOException("Position ${dataSpec.position} is past the end of the file")
		}
		remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) content.size - dataSpec.position else minOf(dataSpec.length, content.size - dataSpec.position)
		stream = try {
			content.openStream(dataSpec.position, remaining)
		} catch (e: BackendException) {
			throw IOException(e)
		}
		transferStarted(dataSpec)
		return remaining
	}

	@Throws(IOException::class)
	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		if (length == 0) {
			return 0
		}
		if (remaining == 0L) {
			return C.RESULT_END_OF_INPUT
		}
		val read = (stream ?: throw IOException("Source is not open")).read(buffer, offset, minOf(length.toLong(), remaining).toInt())
		if (read < 0) {
			return C.RESULT_END_OF_INPUT
		}
		remaining -= read
		bytesTransferred(read)
		return read
	}

	override fun getUri(): Uri? = uri

	override fun close() {
		try {
			stream?.close()
		} finally {
			stream = null
			if (uri != null) {
				uri = null
				transferEnded()
			}
		}
	}

	/** Opens the content on the player's loading thread, the first time a source is created, and shares it between sources. */
	class Factory(private val open: () -> RandomAccessContent) : DataSource.Factory {

		private var content: RandomAccessContent? = null

		override fun createDataSource(): DataSource = RandomAccessDataSource(synchronized(this) { content ?: open().also { content = it } })

		fun release() {
			synchronized(this) {
				content?.close()
				content = null
			}
		}
	}
}
