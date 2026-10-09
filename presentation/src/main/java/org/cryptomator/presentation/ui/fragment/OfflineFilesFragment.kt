package org.cryptomator.presentation.ui.fragment

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
		binding.rvVaults.recyclerView.layoutManager = LinearLayoutManager(context())
		binding.rvVaults.recyclerView.adapter = adapter
		binding.removeAll.setOnClickListener { presenter.onRemoveAllClicked() }
		presenter.loadUsage()
	}

	fun showUsage(vaults: List<OfflineVaultModel>) {
		adapter.clear()
		adapter.addAll(vaults)
		binding.total.text = if (vaults.isEmpty()) {
			getString(R.string.screen_offline_files_empty)
		} else {
			getString(R.string.screen_offline_files_total, fileSizeHelper.getFormattedFileSize(vaults.sumOf { it.bytes }))
		}
		binding.removeAll.isEnabled = vaults.isNotEmpty()
	}
}
