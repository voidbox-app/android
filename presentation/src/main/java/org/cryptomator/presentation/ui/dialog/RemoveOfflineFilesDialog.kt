package org.cryptomator.presentation.ui.dialog

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogRemoveOfflineFilesBinding
import org.cryptomator.presentation.model.OfflineVaultModel

@Dialog
class RemoveOfflineFilesDialog : BaseDialog<RemoveOfflineFilesDialog.Callback, DialogRemoveOfflineFilesBinding>(DialogRemoveOfflineFilesBinding::inflate) {

	interface Callback {

		/** [vault] is null when every vault's offline files are to be removed. */
		fun onRemoveOfflineFilesConfirmed(vault: OfflineVaultModel?)
	}

	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		val vault = requireArguments().getSerializable(VAULT_ARG) as OfflineVaultModel?
		val title = vault?.let { it.vault?.name ?: getString(R.string.screen_offline_files_removed_vault) } ?: getString(R.string.dialog_remove_offline_files_title)
		return builder //
			.setTitle(title) //
			.setPositiveButton(getString(R.string.screen_offline_files_remove)) { _: DialogInterface, _: Int -> callback?.onRemoveOfflineFilesConfirmed(vault) } //
			.setNegativeButton(getString(R.string.dialog_button_cancel)) { _: DialogInterface, _: Int -> } //
			.create()
	}

	public override fun setupView() {
		// empty
	}

	companion object {

		private const val VAULT_ARG = "vault"

		fun newInstance(vault: OfflineVaultModel?): DialogFragment {
			val dialog = RemoveOfflineFilesDialog()
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vault)
			dialog.arguments = args
			return dialog
		}
	}
}
