package org.cryptomator.presentation.util

import org.cryptomator.domain.repository.RandomAccessContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class TextPagesTest {

	private var closed = false

	private fun pagesOf(text: String): TextPages {
		val bytes = text.toByteArray(StandardCharsets.UTF_8)
		return TextPages(object : RandomAccessContent {
			override val size: Long = bytes.size.toLong()

			override fun openStream(offset: Long, length: Long?): InputStream {
				val end = if (length == null) bytes.size else minOf(bytes.size.toLong(), offset + length).toInt()
				return ByteArrayInputStream(bytes, offset.toInt(), end - offset.toInt())
			}

			override fun close() {
				closed = true
			}
		})
	}

	private fun TextPages.indexAll(): List<Int> {
		val counts = ArrayList<Int>()
		index { counts.add(it) }
		return counts
	}

	private fun TextPages.allPages(): List<String> = (0 until pageCount).map { page(it) }

	@Test
	fun `an empty text has no pages`() {
		val pages = pagesOf("")

		pages.indexAll()

		assertEquals(0, pages.pageCount)
		assertTrue(pages.indexed)
	}

	@Test
	fun `a short text is one page`() {
		val pages = pagesOf("first line\nsecond line\n")

		val counts = pages.indexAll()

		assertEquals(1, pages.pageCount)
		assertEquals(listOf(1), counts)
		assertEquals("first line\nsecond line\n", pages.page(0))
	}

	@Test
	fun `pages are cut at line breaks once a page is full`() {
		val line = "x".repeat(1023) + "\n"
		val text = line.repeat(100)
		val pages = pagesOf(text)

		pages.indexAll()

		assertEquals(4, pages.pageCount)
		pages.allPages().dropLast(1).forEach { page ->
			assertTrue(page.endsWith("\n"))
			assertEquals(TextPages.PAGE_BYTES, page.toByteArray(StandardCharsets.UTF_8).size)
		}
		assertEquals(text, pages.allPages().joinToString(""))
	}

	@Test
	fun `a page without line breaks is cut between characters, never inside one`() {
		val text = "ж".repeat(MAX_PAGE_CHARS * 2 + 7)
		val pages = pagesOf(text)

		pages.indexAll()

		assertEquals(3, pages.pageCount)
		pages.allPages().forEach { page -> assertFalse(page.contains('�')) }
		assertEquals(text, pages.allPages().joinToString(""))
	}

	@Test
	fun `the running page count is reported while indexing`() {
		val pages = pagesOf(("y".repeat(1023) + "\n").repeat(200))

		val counts = pages.indexAll()

		assertEquals(1, counts.first())
		assertEquals(pages.pageCount, counts.last())
		assertEquals(counts, counts.sorted())
	}

	@Test
	fun `find walks forward across pages ignoring case`() {
		val filler = ("z".repeat(1023) + "\n").repeat(40)
		val pages = pagesOf("Needle" + filler + "needle" + filler + "NEEDLE")
		pages.indexAll()

		val first = pages.find("needle", null, forward = true)
		val second = pages.find("needle", first, forward = true)
		val third = pages.find("needle", second, forward = true)
		val none = pages.find("needle", third, forward = true)

		assertEquals(TextPages.Match(0, 0, 6), first)
		assertTrue(second!!.page > 0)
		assertTrue(third!!.page > second.page)
		assertNull(none)
	}

	@Test
	fun `find walks backward across pages`() {
		val filler = ("z".repeat(1023) + "\n").repeat(40)
		val pages = pagesOf("needle" + filler + "needle" + filler + "needle")
		pages.indexAll()

		val last = pages.find("needle", null, forward = false)
		val middle = pages.find("needle", last, forward = false)
		val first = pages.find("needle", middle, forward = false)
		val none = pages.find("needle", first, forward = false)

		assertEquals(pages.pageCount - 1, last!!.page)
		assertTrue(middle!!.page in 1 until last.page)
		assertEquals(TextPages.Match(0, 0, 6), first)
		assertNull(none)
	}

	@Test
	fun `find finds nothing for an empty query`() {
		val pages = pagesOf("some text")
		pages.indexAll()

		assertNull(pages.find("", null, forward = true))
	}

	@Test
	fun `closing the pages closes the content`() {
		val pages = pagesOf("some text")

		pages.close()

		assertTrue(closed)
	}

	companion object {

		private const val MAX_PAGE_CHARS = TextPages.MAX_PAGE_BYTES / 2
	}
}
