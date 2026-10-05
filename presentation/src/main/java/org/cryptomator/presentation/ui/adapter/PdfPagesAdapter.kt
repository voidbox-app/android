package org.cryptomator.presentation.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.davemorrissey.labs.subscaleview.ImageSource
import org.cryptomator.presentation.databinding.ItemPdfPageBinding
import org.cryptomator.presentation.util.PdfPages

/** One zoomable page per item; pages render lazily and stay in the PdfPages cache. */
class PdfPagesAdapter(private val pages: PdfPages) : RecyclerView.Adapter<PdfPagesAdapter.PageHolder>() {

	override fun getItemCount(): Int = pages.pageCount

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
		return PageHolder(ItemPdfPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
	}

	override fun onBindViewHolder(holder: PageHolder, position: Int) {
		holder.bind(position)
	}

	override fun onViewRecycled(holder: PageHolder) {
		holder.binding.pageView.recycle()
	}

	inner class PageHolder(val binding: ItemPdfPageBinding) : RecyclerView.ViewHolder(binding.root) {

		fun bind(position: Int) {
			binding.pageView.recycle()
			binding.progress.visibility = View.VISIBLE
			val width = binding.root.resources.displayMetrics.widthPixels * 2
			pages.render(position, width) { bitmap ->
				if (bindingAdapterPosition == position) {
					binding.pageView.setImage(ImageSource.cachedBitmap(bitmap))
					binding.progress.visibility = View.GONE
				}
			}
		}
	}
}
