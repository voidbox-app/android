package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.LinearLayoutManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentTextViewerBinding
import org.cryptomator.presentation.presenter.TextEditorPresenter
import org.cryptomator.presentation.ui.adapter.TextPagesAdapter
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import org.cryptomator.presentation.util.TextPages
import javax.inject.Inject

/** Shows a text too large for the editor page by page, decrypting only the pages on screen. */
@Fragment
class TextViewerFragment : BaseFragment<FragmentTextViewerBinding>(FragmentTextViewerBinding::inflate), TextSearch {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	private var adapter: TextPagesAdapter? = null
	private var searching = false
	private val subscriptions = CompositeDisposable()

	override fun setupView() {
		// no-op
	}

	override fun loadContent() {
		textEditorPresenter.loadFileContent()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.pages.layoutManager = LinearLayoutManager(requireContext())
		binding.pages.applySystemBarsPadding(left = true, right = true, bottom = true)
	}

	fun showPages() {
		if (adapter != null || !::textEditorPresenter.isInitialized) {
			return
		}
		val pages = textEditorPresenter.pages ?: return
		val pagesAdapter = TextPagesAdapter(pages, textEditorPresenter::loadPage)
		pagesAdapter.highlight(textEditorPresenter.currentMatch)
		adapter = pagesAdapter
		binding.pages.adapter = pagesAdapter
		showProgressBar()
		subscriptions.add(
			textEditorPresenter.pageCount() //
				.observeOn(AndroidSchedulers.mainThread()) //
				.subscribe({ pagesAdapter.showPageCount(it) }, { textEditorPresenter.showError(it) }, { showProgressBar() })
		)
	}

	private fun showProgressBar() {
		val indexing = textEditorPresenter.pages?.indexed == false
		binding.progress.visibility = if (indexing || searching) View.VISIBLE else View.GONE
	}

	override fun onQueryText(query: String) {
		textEditorPresenter.startNewPageSearch(query)
		adapter?.highlight(null)
		if (query.isEmpty()) {
			return
		}
		onNextQuery()
	}

	override fun onPreviousQuery() {
		search(forward = false)
	}

	override fun onNextQuery() {
		search(forward = true)
	}

	private fun search(forward: Boolean) {
		if (adapter == null || textEditorPresenter.query.isNullOrEmpty()) {
			return
		}
		searching = true
		showProgressBar()
		textEditorPresenter.findInPages(forward) { match ->
			searching = false
			showProgressBar()
			showMatch(match)
		}
	}

	private fun showMatch(match: TextPages.Match?) {
		val pagesAdapter = adapter ?: return
		if (match == null) {
			return
		}
		pagesAdapter.highlight(match)
		val layoutManager = binding.pages.layoutManager as LinearLayoutManager
		layoutManager.scrollToPositionWithOffset(match.page, 0)
		binding.pages.doOnPreDraw { scrollMatchLineToTop(match) }
	}

	private fun scrollMatchLineToTop(match: TextPages.Match) {
		val holder = binding.pages.findViewHolderForAdapterPosition(match.page) ?: return
		val pageText = holder.itemView as? TextView ?: return
		val layout = pageText.layout ?: return
		if (match.index > pageText.length()) {
			return
		}
		val lineTop = layout.getLineTop(layout.getLineForOffset(match.index))
		binding.pages.scrollBy(0, pageText.top + pageText.paddingTop + lineTop - binding.pages.paddingTop)
	}

	override fun onDestroyView() {
		subscriptions.clear()
		adapter = null
		binding.pages.adapter = null
		super.onDestroyView()
	}
}
