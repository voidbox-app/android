package org.cryptomator.presentation.ui.activity

import androidx.viewpager2.widget.ViewPager2
import org.cryptomator.generator.Activity
import org.cryptomator.generator.InjectIntent
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityPdfPreviewBinding
import org.cryptomator.presentation.intent.PdfPreviewIntent
import org.cryptomator.presentation.presenter.PdfPreviewPresenter
import org.cryptomator.presentation.ui.activity.view.PdfPreviewView
import org.cryptomator.presentation.ui.adapter.PdfPagesAdapter
import org.cryptomator.presentation.util.PdfPages
import javax.inject.Inject
import timber.log.Timber

/** In-app PDF viewer on the platform renderer: pages swipe horizontally and zoom like photos. */
@Activity
class PdfPreviewActivity : BaseActivity<ActivityPdfPreviewBinding>(ActivityPdfPreviewBinding::inflate), PdfPreviewView {

	@Inject
	lateinit var presenter: PdfPreviewPresenter

	@InjectIntent
	lateinit var pdfPreviewIntent: PdfPreviewIntent

	private var pages: PdfPages? = null

	override fun setupView() {
		val file = pdfPreviewIntent.pdfFile()
		binding.mtToolbar.toolbar.title = file.name
		setSupportActionBar(binding.mtToolbar.toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		val pdfPages = try {
			presenter.openPages(file)
		} catch (e: Exception) {
			// a password-protected or damaged file; the platform renderer reports both as exceptions
			Timber.tag("PdfPreview").e(e, "Could not open %s", file.name)
			showError(getString(R.string.screen_pdf_preview_error))
			finish()
			return
		}
		pages = pdfPages
		binding.pager.adapter = PdfPagesAdapter(pdfPages)
		binding.pager.offscreenPageLimit = 1
		binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				showPageNumber(position)
			}
		})
		showPageNumber(0)
	}

	private fun showPageNumber(position: Int) {
		binding.mtToolbar.toolbar.subtitle = getString(R.string.screen_pdf_preview_page, position + 1, pages?.pageCount ?: 0)
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}

	override fun onDestroy() {
		pages?.close()
		pages = null
		if (isFinishing) {
			presenter.deleteDecryptedCopy(pdfPreviewIntent.pdfFile())
		}
		super.onDestroy()
	}
}
