package org.cryptomator.data.util

import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.RandomAccessContent
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class FileRandomAccessContent(private val file: File) : RandomAccessContent {

	override val size: Long = file.length()

	override fun openStream(offset: Long, length: Long?): InputStream {
		return try {
			val stream = FileInputStream(file)
			stream.channel.position(offset)
			BoundedInputStream(stream, length)
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	override fun close() = Unit
}
