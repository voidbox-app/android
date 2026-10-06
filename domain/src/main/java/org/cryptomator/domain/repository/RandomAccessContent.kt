package org.cryptomator.domain.repository

import org.cryptomator.domain.exception.BackendException
import java.io.Closeable
import java.io.InputStream

/** A file read by range; every [openStream] requests exactly that range. */
interface RandomAccessContent : Closeable {

	val size: Long

	/**
	 * [length] bytes from [offset], or up to the end when null.
	 *
	 * @throws org.cryptomator.domain.exception.RandomAccessNotSupportedException if the server ignores the range
	 */
	@Throws(BackendException::class)
	fun openStream(offset: Long, length: Long?): InputStream
}
