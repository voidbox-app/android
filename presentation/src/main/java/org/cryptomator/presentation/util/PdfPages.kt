package org.cryptomator.presentation.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

/**
 * Renders the pages of a PDF with the platform's PdfRenderer. Rendering runs on one background
 * thread (the renderer is not thread-safe) and finished pages stay in a size-bounded cache.
 */
class PdfPages(file: File) : Closeable {

	private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
	private val renderer = PdfRenderer(descriptor)
	private val executor = Executors.newSingleThreadExecutor()
	private val mainThread = Handler(Looper.getMainLooper())
	private val cache = object : LruCache<Int, Bitmap>(CACHE_BYTES) {
		override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
	}

	@Volatile
	private var closed = false

	val pageCount: Int = renderer.pageCount

	fun render(index: Int, targetWidth: Int, onRendered: (Bitmap) -> Unit) {
		cache.get(index)?.let {
			onRendered(it)
			return
		}
		executor.execute {
			if (closed) {
				return@execute
			}
			val bitmap = renderer.openPage(index).use { page ->
				val width = targetWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)
				val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
				Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
					// pages have no background of their own
					it.eraseColor(Color.WHITE)
					page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
				}
			}
			cache.put(index, bitmap)
			mainThread.post {
				if (!closed) {
					onRendered(bitmap)
				}
			}
		}
	}

	override fun close() {
		closed = true
		// queued renders check the flag; the renderer itself closes behind them on its own thread
		executor.execute {
			renderer.close()
			descriptor.close()
		}
		executor.shutdown()
		cache.evictAll()
	}

	companion object {

		private const val CACHE_BYTES = 48 * 1024 * 1024
		private const val MIN_WIDTH = 320
		private const val MAX_WIDTH = 2400
	}
}
