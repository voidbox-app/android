package org.cryptomator.presentation.util

import android.media.MediaDataSource
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.RandomAccessContent
import java.io.IOException
import java.io.InputStream
import java.util.TreeMap

/** MediaDataSource for the frame extractor. Head and tail are prefetched in fixed sizes; reading stops after [budget] bytes. */
class RandomAccessMediaDataSource(private val content: RandomAccessContent, private val budget: Long = DEFAULT_BUDGET) : MediaDataSource() {

	// by start offset; pieces never overlap
	private val pieces = TreeMap<Long, ByteArray>()
	private var fetched = 0L

	@Volatile
	private var closed = false

	init {
		val head = minOf(content.size, WINDOW)
		fetch(0, head)
		if (content.size > head) {
			fetch(maxOf(head, content.size - WINDOW), content.size)
		}
	}

	override fun getSize(): Long = content.size

	@Throws(IOException::class)
	override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
		if (position >= content.size) {
			return -1
		}
		val count = minOf(size.toLong(), content.size - position).toInt()
		var copied = 0
		while (copied < count) {
			val at = position + copied
			val (start, piece) = pieceAt(at)
			val inPiece = (at - start).toInt()
			val chunk = minOf(count - copied, piece.size - inPiece)
			System.arraycopy(piece, inPiece, buffer, offset + copied, chunk)
			copied += chunk
		}
		return copied
	}

	override fun close() {
		closed = true
		synchronized(pieces) { pieces.clear() }
		content.close()
	}

	@Throws(IOException::class)
	private fun pieceAt(position: Long): Pair<Long, ByteArray> {
		synchronized(pieces) {
			pieces.floorEntry(position)?.let { if (position < it.key + it.value.size) return it.key to it.value }
		}
		// one block around the position, ending where a fetched piece begins
		val from = position / BLOCK * BLOCK
		val to = synchronized(pieces) { minOf(from + BLOCK, content.size, pieces.ceilingKey(position) ?: Long.MAX_VALUE) }
		val start = synchronized(pieces) { pieces.floorEntry(from)?.let { maxOf(from, it.key + it.value.size) } ?: from }
		fetch(start, to)
		synchronized(pieces) {
			pieces.floorEntry(position)?.let { if (position < it.key + it.value.size) return it.key to it.value }
		}
		throw IOException("Bytes at $position were not fetched")
	}

	@Throws(IOException::class)
	private fun fetch(from: Long, to: Long) {
		if (closed) {
			throw IOException("Source is closed")
		}
		if (fetched + (to - from) > budget) {
			throw IOException("Fetching more than $budget bytes for one frame")
		}
		val bytes = try {
			content.openStream(from, to - from).use { readFully(it, (to - from).toInt()) }
		} catch (e: BackendException) {
			throw IOException(e)
		}
		if (bytes.isEmpty()) {
			throw IOException("No bytes at $from")
		}
		fetched += bytes.size
		synchronized(pieces) { pieces[from] = bytes }
	}

	private fun readFully(stream: InputStream, length: Int): ByteArray {
		val bytes = ByteArray(length)
		var filled = 0
		while (filled < length) {
			val read = stream.read(bytes, filled, length - filled)
			if (read < 0) {
				break
			}
			filled += read
		}
		return if (filled == length) bytes else bytes.copyOf(filled)
	}

	companion object {

		private const val BLOCK = 256L * 1024L
		private const val WINDOW = 1024L * 1024L
		const val DEFAULT_BUDGET = 16L * 1024L * 1024L
	}
}
