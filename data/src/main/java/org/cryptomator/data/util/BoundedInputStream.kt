package org.cryptomator.data.util

import java.io.FilterInputStream
import java.io.InputStream

/** Ends after [limit] bytes; null means no limit. */
open class BoundedInputStream(delegate: InputStream, private val limit: Long?) : FilterInputStream(delegate) {

	private var consumed = 0L

	override fun read(): Int {
		if (limit != null && consumed >= limit) {
			return -1
		}
		val byte = super.read()
		if (byte >= 0) {
			consumed++
		}
		return byte
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		val allowed = if (limit == null) length else minOf(length.toLong(), limit - consumed).toInt()
		if (allowed <= 0) {
			return if (length == 0) 0 else -1
		}
		val read = super.read(buffer, offset, allowed)
		if (read > 0) {
			consumed += read
		}
		return read
	}

	override fun skip(count: Long): Long {
		val allowed = if (limit == null) count else minOf(count, limit - consumed)
		val skipped = super.skip(allowed.coerceAtLeast(0))
		consumed += skipped
		return skipped
	}

	override fun available(): Int {
		val available = super.available()
		return if (limit == null) available else minOf(available.toLong(), limit - consumed).toInt().coerceAtLeast(0)
	}

	override fun markSupported(): Boolean = false
}
