package org.cryptomator.domain.repository

import org.cryptomator.domain.exception.BackendException
import java.io.Closeable
import java.io.InputStream

/**
 * A file opened for reading parts of it at arbitrary positions, without downloading it whole.
 * Every [openStream] asks the cloud for exactly the requested range and nothing else.
 */
interface RandomAccessContent : Closeable {

	/** Size of the content in bytes. */
	val size: Long

	/**
	 * The bytes from [offset] on: [length] of them, or up to the end when [length] is null.
	 * The stream ends early when the content does.
	 *
	 * @throws org.cryptomator.domain.exception.RandomAccessNotSupportedException if the server answered with something other than the requested range
	 */
	@Throws(BackendException::class)
	fun openStream(offset: Long, length: Long?): InputStream
}
