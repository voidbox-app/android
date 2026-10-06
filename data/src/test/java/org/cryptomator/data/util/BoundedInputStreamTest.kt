package org.cryptomator.data.util

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class BoundedInputStreamTest {

	private val bytes = ByteArray(100) { it.toByte() }

	@Test
	fun stopsAtTheLimit() {
		val stream = BoundedInputStream(ByteArrayInputStream(bytes), 10)
		assertArrayEquals(bytes.copyOfRange(0, 10), stream.readBytes())
		assertEquals(-1, stream.read())
	}

	@Test
	fun limitLargerThanTheStreamReadsEverything() {
		assertArrayEquals(bytes, BoundedInputStream(ByteArrayInputStream(bytes), 1000).readBytes())
	}

	@Test
	fun noLimitReadsEverything() {
		assertArrayEquals(bytes, BoundedInputStream(ByteArrayInputStream(bytes), null).readBytes())
	}

	@Test
	fun singleByteReadsAndSkipsCountTowardsTheLimit() {
		val stream = BoundedInputStream(ByteArrayInputStream(bytes), 5)
		assertEquals(0, stream.read())
		assertEquals(2, stream.skip(2))
		assertEquals(3, stream.read())
		assertEquals(1, stream.read(ByteArray(10), 0, 10))
		assertEquals(-1, stream.read())
	}
}
