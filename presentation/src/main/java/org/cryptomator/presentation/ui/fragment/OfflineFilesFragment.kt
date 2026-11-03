package org.cryptomator.presentation.ui.fragment

import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentOfflineFilesBinding
import org.cryptomator.presentation.model.OfflineVaultModel
import org.cryptomator.presentation.presenter.OfflineFilesPresenter
import org.cryptomator.presentation.ui.adapter.OfflineFilesAdapter
import org.cryptomator.presentation.util.FileSizeHelper
import javax.inject.Inject

@Fragment
class OfflineFilesFragment : BaseFragment<FragmentOfflineFilesBinding>(FragmentOfflineFilesBinding::inflate) {

	@Inject
	lateinit var adapter: OfflineFilesAdapter

	@Inject
	lateinit var presenter: OfflineFilesPresenter

	@Inject
	lateinit var fileSizeHelper: FileSizeHelper

	override fun setupView() {
		adapter.setCallback(object : OfflineFilesAdapter.Callback {
			override fun onRemoveClicked(vault: OfflineVaultModel) {
				presenter.onRemoveClicked(vault)
			}
		})
		binding.vaults.layoutManager = LinearLayoutManager(context())
		binding.vaults.adapter = adapter
		binding.removeAll.setOnClickListener { presenter.onRemoveAllClicked() }
		presenter.loadUsage()
	}

	fun showUsage(vaults: List<OfflineVaultModel>) {
		adapter.clear()
		adapter.addAll(vaults)
		binding.total.text = fileSizeHelper.getFormattedFileSize(vaults.sumOf { it.bytes })
		binding.totalSubtitle.text = if (vaults.isEmpty()) {
			getString(R.string.screen_offline_files_empty)
		} else {
			resources.getQuantityString(R.plurals.screen_offline_files_in_vaults, vaults.size, vaults.size)
		}
		showUsageBar(vaults)
		binding.vaultsHeader.visibility = if (vaults.isEmpty()) View.GONE else View.VISIBLE
		binding.removeAll.isEnabled = vaults.isNotEmpty()
	}

	/** One rounded segment per vault, in proportion to its bytes; the smallest share still shows. */
	private fun showUsageBar(vaults: List<OfflineVaultModel>) {
		val bar = binding.usageBar
		bar.removeAllViews()
		bar.visibility = if (vaults.isEmpty()) View.INVISIBLE else View.VISIBLE
		val total = vaults.sumOf { it.bytes }.coerceAtLeast(1)
		val gap = (2 * resources.displayMetrics.density).toInt()
		vaults.forEachIndexed { index, vault ->
			val segment = View(context())
			segment.background = ContextCompat.getDrawable(context(), R.drawable.bg_usage_segment)
			segment.backgroundTintList = ColorStateList.valueOf(OfflineFilesAdapter.segmentColor(bar, index))
			val weight = (vault.bytes.toFloat() / total).coerceAtLeast(MIN_SHARE)
			val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight)
			if (index < vaults.lastIndex) {
				params.marginEnd = gap
			}
			bar.addView(segment, params)
		}
	}

	companion object {

		private const val MIN_SHARE = 0.02f
	}
}
