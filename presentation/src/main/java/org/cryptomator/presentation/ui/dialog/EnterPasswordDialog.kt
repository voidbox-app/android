package org.cryptomator.presentation.ui.dialog

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.cryptomator.generator.Dialog
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogEnterPasswordBinding
import org.cryptomator.presentation.databinding.ViewDialogErrorBinding
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.model.VaultModel

@Dialog(secure = true)
class EnterPasswordDialog : BaseProgressErrorDialog<EnterPasswordDialog.Callback, DialogEnterPasswordBinding>(DialogEnterPasswordBinding::inflate) {

	interface Callback {

		fun onUnlockClick(vaultModel: VaultModel, password: String)
		fun closeDialog()
		fun onUnlockCanceled()
	}

	// The prompt is a modal bottom sheet, not an AlertDialog: the password field sits within thumb reach
	// and the sheet rises with the keyboard.
	override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
		binding = DialogEnterPasswordBinding.inflate(LayoutInflater.from(context), null, false)
		val dialog = BottomSheetDialog(requireContext())
		dialog.setContentView(binding.root)
		dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
		dialog.behavior.skipCollapsed = true
		dialog.setCanceledOnTouchOutside(false)
		dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
		dialog.window?.decorView?.filterTouchesWhenObscured = disableDialogWhenObscured()
		dialog.setOnKeyListener { _, keyCode, _ ->
			if (keyCode == KeyEvent.KEYCODE_BACK) {
				cancel()
				true
			} else {
				false
			}
		}
		return dialog
	}

	// The sheet already holds the content view, so the fragment itself has none.
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		return null
	}

	override fun onStart() {
		super.onStart()
		applySecureScreenFlag()
		setupView()
		binding.etPassword.requestFocus()
	}

	// Unused: the sheet is built in onCreateDialog.
	public override fun setupDialog(builder: AlertDialog.Builder): android.app.Dialog {
		return builder.create()
	}

	fun vaultModel(): VaultModel {
		return requireArguments().getSerializable(VAULT_ARG) as VaultModel
	}

	public override fun setupView() {
		val vaultModel = vaultModel()
		binding.tvVaultName.text = vaultModel.name
		binding.tvVaultLocation.text = getString(R.string.dialog_enter_password_location, getString(vaultModel.cloudType.displayNameResource), vaultModel.path)
		binding.btnUnlock.isEnabled = false
		binding.btnUnlock.setOnClickListener { unlock() }
		binding.btnCancel.setOnClickListener { cancel() }
		registerOnEditorDoneActionAndPerformButtonClick(binding.etPassword) { binding.btnUnlock }
		binding.etPassword.addTextChangedListener(object : TextWatcher {
			override fun afterTextChanged(s: Editable) {
				binding.btnUnlock.isEnabled = s.toString().isNotEmpty()
			}

			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
		})
		dialog?.let { showKeyboard(it) }
	}

	private fun unlock() {
		showProgress(ProgressModel(ProgressStateModel.UNLOCKING_VAULT))
		callback?.onUnlockClick(vaultModel(), binding.etPassword.text.toString())
		onWaitForResponse(binding.etPassword)
		setButtonsEnabled(false)
	}

	private fun cancel() {
		dialog?.dismiss()
		callback?.onUnlockCanceled()
		callback?.closeDialog()
	}

	override fun showError(message: String) {
		super.showError(message)
		setButtonsEnabled(true)
	}

	private fun setButtonsEnabled(enabled: Boolean) {
		binding.btnCancel.isEnabled = enabled
		binding.btnUnlock.isEnabled = enabled && !binding.etPassword.text.isNullOrEmpty()
	}

	override fun dialogProgressLayout(): LinearLayout {
		return binding.llDialogProgress.llProgress
	}

	override fun dialogProgressTextView(): TextView {
		return binding.llDialogProgress.tvProgress
	}

	override fun dialogErrorBinding(): ViewDialogErrorBinding {
		return binding.llDialogError
	}

	override fun enableViewAfterError(): View {
		return binding.etPassword
	}

	companion object {

		private const val VAULT_ARG = "vault"

		fun newInstance(vaultModel: VaultModel): EnterPasswordDialog {
			val dialog = EnterPasswordDialog()
			val args = Bundle()
			args.putSerializable(VAULT_ARG, vaultModel)
			dialog.arguments = args
			return dialog
		}
	}
}
