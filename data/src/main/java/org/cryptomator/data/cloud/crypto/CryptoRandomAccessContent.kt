package org.cryptomator.data.cloud.crypto

import org.cryptomator.cryptolib.api.AuthenticationFailedException
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.FileHeader
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.RandomAccessContent
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Cleartext of a vault file read in pieces: every chunk of the vault format is encrypted on its
 * own, so a range of cleartext costs only the header plus the chunks that overlap the range,
 * fetched from the cloud as one contiguous ciphertext range.
 */
internal class CryptoRandomAccessContent(private val ciphertext: RandomAccessContent, private val cryptor: Cryptor) : RandomAccessContent {

	private val headerSize = cryptor.fileHeaderCryptor().headerSize()
	private val cleartextChunkSize = cryptor.fileContentCryptor().cleartextChunkSize()
	private val ciphertextChunkSize = cryptor.fileContentCryptor().ciphertextChunkSize()

	override val size: Long = cryptor.fileContentCryptor().cleartextSize((ciphertext.size - headerSize).coerceAtLeast(0))

	@Volatile
	private var header: FileHeader? = null

	@Throws(BackendException::class)
	override fun openStream(offset: Long, length: Long?): InputStream {
		require(offset >= 0) { "Negative offset $offset" }
		val end = if (length == null) size else minOf(size, offset + length)
		if (offset >= end) {
			return ByteArrayInputStream(ByteArray(0))
		}
		val header = header()
		val firstChunk = offset / cleartextChunkSize
		val lastChunk = (end - 1) / cleartextChunkSize
		val chunks = ciphertext.openStream(headerSize + firstChunk * ciphertextChunkSize, (lastChunk - firstChunk + 1) * ciphertextChunkSize)
		return DecryptingStream(chunks, header, firstChunk, offset - firstChunk * cleartextChunkSize, end - offset)
	}

	override fun close() {
		ciphertext.close()
	}

	@Throws(BackendException::class)
	private fun header(): FileHeader {
		header?.let { return it }
		synchronized(this) {
			header?.let { return it }
			val bytes = ByteBuffer.allocate(headerSize)
			try {
				ciphertext.openStream(0, headerSize.toLong()).use { stream -> readFully(stream, bytes) }
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
			if (bytes.hasRemaining()) {
				throw FatalBackendException("File is shorter than its header")
			}
			bytes.flip()
			return try {
				cryptor.fileHeaderCryptor().decryptHeader(bytes).also { header = it }
			} catch (e: AuthenticationFailedException) {
				throw FatalBackendException(e)
			}
		}
	}

	/** Decrypts chunk after chunk from the ciphertext stream, dropping [skip] cleartext bytes first and stopping after [remaining]. */
	private inner class DecryptingStream(private val chunks: InputStream, private val header: FileHeader, firstChunk: Long, skip: Long, private var remaining: Long) : InputStream() {

		private var chunkNumber = firstChunk
		private var toSkip = skip
		private val encrypted = ByteBuffer.allocate(ciphertextChunkSize)
		private var cleartext: ByteBuffer = ByteBuffer.allocate(0)

		override fun read(): Int {
			val single = ByteArray(1)
			return if (read(single, 0, 1) == 1) single[0].toInt() and 0xff else -1
		}

		override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
			if (length == 0) {
				return 0
			}
			if (remaining <= 0) {
				return -1
			}
			while (!cleartext.hasRemaining()) {
				if (!nextChunk()) {
					return -1
				}
			}
			val count = minOf(length.toLong(), cleartext.remaining().toLong(), remaining).toInt()
			cleartext.get(buffer, offset, count)
			remaining -= count
			return count
		}

		// one ciphertext chunk in, its cleartext out, minus whatever lies before the requested offset
		private fun nextChunk(): Boolean {
			encrypted.clear()
			readFully(chunks, encrypted)
			if (encrypted.position() == 0) {
				return false
			}
			encrypted.flip()
			cleartext = try {
				cryptor.fileContentCryptor().decryptChunk(encrypted, chunkNumber, header, true)
			} catch (e: AuthenticationFailedException) {
				throw IOException("Chunk $chunkNumber failed authentication", e)
			}
			chunkNumber++
			if (toSkip > 0) {
				val dropped = minOf(toSkip, cleartext.remaining().toLong()).toInt()
				cleartext.position(cleartext.position() + dropped)
				toSkip -= dropped
			}
			return true
		}

		override fun available(): Int = minOf(cleartext.remaining().toLong(), remaining).toInt()

		override fun close() {
			chunks.close()
		}
	}

	private fun readFully(stream: InputStream, into: ByteBuffer) {
		while (into.hasRemaining()) {
			val read = stream.read(into.array(), into.arrayOffset() + into.position(), into.remaining())
			if (read < 0) {
				break
			}
			into.position(into.position() + read)
		}
	}
}
