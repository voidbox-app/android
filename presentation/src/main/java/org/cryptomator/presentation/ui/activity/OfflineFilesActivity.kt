package org.cryptomator.presentation.ui.activity

import androidx.fragment.app.Fragment
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.model.OfflineVaultModel
import org.cryptomator.presentation.presenter.OfflineFilesPresenter
import org.cryptomator.presentation.ui.activity.view.OfflineFilesView
import org.cryptomator.presentation.ui.dialog.RemoveOfflineFilesDialog
import org.cryptomator.presentation.ui.fragment.OfflineFilesFragment
import javax.inject.Inject

@Activity
class OfflineFilesActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate), //
	OfflineFilesView, //
	RemoveOfflineFilesDialog.Callback {

	@Inject
	lateinit var presenter: OfflineFilesPresenter

	override fun setupView() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_settings_offline_files)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

	override fun createFragment(): Fragment = OfflineFilesFragment()

	override fun showUsage(vaults: List<OfflineVaultModel>) {
		offlineFilesFragment().showUsage(vaults)
	}

	override fun onRemoveOfflineFilesConfirmed(vault: OfflineVaultModel?) {
		presenter.onRemoveConfirmed(vault)
	}

	private fun offlineFilesFragment(): OfflineFilesFragment = getCurrentFragment(R.id.fragment_container) as OfflineFilesFragment
}
