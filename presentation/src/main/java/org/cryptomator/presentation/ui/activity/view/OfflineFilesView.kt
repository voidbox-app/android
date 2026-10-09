package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.model.OfflineVaultModel

interface OfflineFilesView : View {

	fun showUsage(vaults: List<OfflineVaultModel>)
}
