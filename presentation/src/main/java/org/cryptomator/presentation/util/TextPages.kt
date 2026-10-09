package org.cryptomator.presentation.util

import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.RandomAccessContent
import java.io.Closeable
import java.io.IOException
import java.nio.charset.StandardCharsets

/** A text too large to hold in memory, decoded one page at a time; only the page borders are kept. */
class TextPages(private val content: RandomAccessContent) : Closeable {

	data class Match(val page: Int, val index: Int, val length: Int)

	private val starts = ArrayList<Long>()
	private val cache = object : LinkedHashMap<Int, String>(CACHED_PAGES, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?): Boolean = size > CACHED_PAGES
	}

	@Volatile
	var indexed = false
		private set

	val pageCount: Int
		get() = synchronized(starts) { starts.size }

	/** Reads the text once and cuts it into pages at line breaks; [onPagesAdded] gets the running page count as pages are found. */
	@Throws(BackendException::class, IOException::class)
	fun index(onPagesAdded: (Int) -> Unit) {
		synchronized(starts) { starts.clear() }
		if (content.size == 0L) {
			indexed = true
			return
		}
		addPage(0L)
		onPagesAdded(pageCount)
		val buffer = ByteArray(READ_BUFFER_SIZE)
		var pageStart = 0L
		var position = 0L
		content.openStream(0, null).use { stream ->
			while (true) {
				val read = stream.read(buffer)
				if (read < 0) {
					break
				}
				val before = pageCount
				for (i in 0 until read) {
					val offset = position + i
					val byte = buffer[i].toInt()
					if (offset - pageStart >= MAX_PAGE_BYTES && byte and 0xC0 != 0x80) {
						addPage(offset)
						pageStart = offset
					}
					if (byte == NEWLINE && offset + 1 - pageStart >= PAGE_BYTES && offset + 1 < content.size) {
						addPage(offset + 1)
						pageStart = offset + 1
					}
				}
				position += read
				if (pageCount != before) {
					onPagesAdded(pageCount)
				}
			}
		}
		indexed = true
	}

	fun cachedPage(page: Int): String? = synchronized(cache) { cache[page] }

	/** Call off the main thread. */
	@Throws(BackendException::class, IOException::class)
	fun page(page: Int): String {
		cachedPage(page)?.let { return it }
		val (start, end) = synchronized(starts) {
			starts[page] to (if (page + 1 < starts.size) starts[page + 1] else content.size)
		}
		val text = content.openStream(start, end - start).use { String(it.readBytes(), StandardCharsets.UTF_8) }
		synchronized(cache) { cache[page] = text }
		return text
	}

	/** The next match after [from] (or the first one) when [forward], otherwise the one before it; null when there is none. */
	@Throws(BackendException::class, IOException::class)
	fun find(query: String, from: Match?, forward: Boolean): Match? {
		if (query.isEmpty() || pageCount == 0) {
			return null
		}
		if (forward) {
			var page = from?.page ?: 0
			var fromIndex = from?.let { it.index + 1 } ?: 0
			while (page < pageCount) {
				val index = page(page).indexOf(query, fromIndex, ignoreCase = true)
				if (index >= 0) {
					return Match(page, index, query.length)
				}
				page++
				fromIndex = 0
			}
		} else {
			var page = from?.page ?: (pageCount - 1)
			var fromIndex = from?.let { it.index - 1 } ?: Int.MAX_VALUE
			while (page >= 0) {
				if (fromIndex >= 0) {
					val index = page(page).lastIndexOf(query, fromIndex, ignoreCase = true)
					if (index >= 0) {
						return Match(page, index, query.length)
					}
				}
				page--
				fromIndex = Int.MAX_VALUE
			}
		}
		return null
	}

	override fun close() {
		content.close()
	}

	private fun addPage(start: Long) {
		synchronized(starts) { starts.add(start) }
	}

	companion object {

		const val PAGE_BYTES = 32 * 1024
		const val MAX_PAGE_BYTES = 64 * 1024
		private const val READ_BUFFER_SIZE = 256 * 1024
		private const val CACHED_PAGES = 24
		private const val NEWLINE = '\n'.code
	}
}
