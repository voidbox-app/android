package org.cryptomator.data.cloud.crypto

import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.CryptorProvider
import org.cryptomator.cryptolib.api.Masterkey
import org.cryptomator.cryptolib.common.EncryptingWritableByteChannel
import org.cryptomator.domain.repository.RandomAccessContent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.security.SecureRandom
import java.util.Random

class CryptoRandomAccessContentTest {

	private lateinit var cryptor: Cryptor
	private lateinit var cleartext: ByteArray
	private lateinit var ciphertext: ByteArray
	private val requestedRanges = ArrayList<Pair<Long, Long?>>()

	@BeforeEach
	fun setup() {
		cryptor = CryptorProvider.forScheme(CryptorProvider.Scheme.SIV_GCM).provide(Masterkey(ByteArray(64)), SecureRandom())
		// 3.5 chunks: a short last chunk and offsets across chunk borders
		cleartext = ByteArray(cryptor.fileContentCryptor().cleartextChunkSize() * 7 / 2).also { Random(1).nextBytes(it) }
		val out = ByteArrayOutputStream()
		EncryptingWritableByteChannel(Channels.newChannel(out), cryptor).use { it.write(ByteBuffer.wrap(cleartext)) }
		ciphertext = out.toByteArray()
	}

	private fun content(): CryptoRandomAccessContent {
		val source = object : RandomAccessContent {
			override val size: Long = ciphertext.size.toLong()

			override fun openStream(offset: Long, length: Long?): InputStream {
				requestedRanges.add(offset to length)
				val end = if (length == null) ciphertext.size else minOf(ciphertext.size.toLong(), offset + length).toInt()
				return ByteArrayInputStream(ciphertext, offset.toInt(), end - offset.toInt())
			}

			override fun close() {}
		}
		return CryptoRandomAccessContent(source, cryptor)
	}

	@Test
	fun sizeIsTheCleartextSize() {
		assertEquals(cleartext.size.toLong(), content().size)
	}

	@Test
	fun wholeFileReadsBackUnchanged() {
		assertArrayEquals(cleartext, content().openStream(0, null).use { it.readBytes() })
	}

	@Test
	fun rangesAcrossChunkBordersReadBack() {
		val chunk = cryptor.fileContentCryptor().cleartextChunkSize()
		val content = content()
		for ((offset, length) in listOf(0 to 1, 5 to 100, chunk - 1 to 2, chunk to chunk, chunk / 2 to 2 * chunk, 3 * chunk + 7 to 1000, cleartext.size - 1 to 1)) {
			val expected = cleartext.copyOfRange(offset, minOf(cleartext.size, offset + length))
			assertArrayEquals(expected, content.openStream(offset.toLong(), length.toLong()).use { it.readBytes() }, "range $offset+$length")
		}
	}

	@Test
	fun pastTheEndIsEmpty() {
		assertEquals(0, content().openStream(cleartext.size.toLong(), 10).use { it.readBytes() }.size)
		assertEquals(0, content().openStream(cleartext.size + 100L, null).use { it.readBytes() }.size)
	}

	@Test
	fun onlyTheHeaderAndOverlappingChunksAreFetched() {
		val chunk = cryptor.fileContentCryptor().cleartextChunkSize()
		val cipherChunk = cryptor.fileContentCryptor().ciphertextChunkSize()
		val header = cryptor.fileHeaderCryptor().headerSize()
		val content = content()
		content.openStream(chunk + 10L, 20).use { it.readBytes() }
		content.openStream(2L * chunk, chunk + 1L).use { it.readBytes() }
		assertEquals(
			listOf(
				0L to header.toLong(), // header, once
				header + cipherChunk.toLong() to cipherChunk.toLong(), // the second chunk only
				header + 2L * cipherChunk to 2L * cipherChunk // the third and fourth chunks
			), requestedRanges
		)
	}
}
