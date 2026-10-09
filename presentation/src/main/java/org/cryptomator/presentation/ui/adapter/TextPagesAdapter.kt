package org.cryptomator.presentation.ui.adapter

import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ItemTextPageBinding
import org.cryptomator.presentation.util.TextPages

/** One decoded page of a large text per item; pages decode lazily off the main thread. */
class TextPagesAdapter(private val pages: TextPages, private val loadPage: (Int, (String) -> Unit) -> Unit) : RecyclerView.Adapter<TextPagesAdapter.PageHolder>() {

	private var pageCount = pages.pageCount
	private var highlighted: TextPages.Match? = null

	override fun getItemCount(): Int = pageCount

	fun showPageCount(count: Int) {
		val added = count - pageCount
		if (added <= 0) {
			return
		}
		pageCount = count
		notifyItemRangeInserted(count - added, added)
	}

	fun highlight(match: TextPages.Match?) {
		val previous = highlighted
		highlighted = match
		previous?.let { notifyItemChanged(it.page) }
		match?.let { notifyItemChanged(it.page) }
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
		return PageHolder(ItemTextPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun onBindViewHolder(holder: PageHolder, position: Int) {
		holder.bind(position)
	}

	inner class PageHolder(private val binding: ItemTextPageBinding) : RecyclerView.ViewHolder(binding.root) {

		fun bind(position: Int) {
			val cached = pages.cachedPage(position)
			if (cached != null) {
				show(position, cached)
				return
			}
			binding.pageText.setText(R.string.screen_text_editor_page_loading)
			loadPage(position) { text ->
				if (bindingAdapterPosition == position) {
					show(position, text)
				}
			}
		}

		private fun show(position: Int, text: String) {
			val match = highlighted?.takeIf { it.page == position && it.index + it.length <= text.length }
			if (match == null) {
				binding.pageText.text = text
				return
			}
			val spannable = SpannableString(text)
			spannable.setSpan(
				BackgroundColorSpan(ContextCompat.getColor(binding.root.context, R.color.colorPrimaryTransparent)),
				match.index,
				match.index + match.length,
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			binding.pageText.text = spannable
		}
	}
}
