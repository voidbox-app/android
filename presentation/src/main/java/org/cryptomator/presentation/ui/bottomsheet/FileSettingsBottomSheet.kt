package org.cryptomator.presentation.ui.bottomsheet

import android.os.Bundle
import android.view.View
import androidx.core.widget.TextViewCompat
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.generator.BottomSheet
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.DialogBottomSheetFileSettingsBinding
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudNodeModel

@BottomSheet(R.layout.dialog_bottom_sheet_file_settings)
class FileSettingsBottomSheet : BaseBottomSheet<FileSettingsBottomSheet.Callback, DialogBottomSheetFileSettingsBinding>(DialogBottomSheetFileSettingsBinding::inflate) {

	interface Callback {

		fun onExportFileClicked(cloudFile: CloudFileModel)
		fun onRenameFileClicked(cloudFile: CloudFileModel)
		fun onDeleteNodeClicked(cloudFile: CloudNodeModel<*>)
		fun onShareFileClicked(cloudFile: CloudFileModel)
		fun onMoveFileClicked(cloudFile: CloudFileModel)
		fun onOpenWithTextFileClicked(cloudFile: CloudFileModel)
		fun onKeepOfflineClicked(cloudFile: CloudFileModel)
		fun onRemoveOfflineClicked(cloudFile: CloudFileModel)
	}

	override fun setupView() {
		val cloudFileModel = requireArguments().getSerializable(FILE_ARG) as CloudFileModel
		val parentFolderPath = requireArguments().getString(PARENT_FOLDER_PATH_ARG)
		val offline = requireArguments().getBoolean(OFFLINE_ARG)

		binding.ivFileImage.setImageResource(cloudFileModel.icon.iconResource)
		binding.tvFileName.text = cloudFileModel.name
		binding.tvFilePath.text = parentFolderPath

		val lowerFileName = cloudFileModel.name.lowercase()
		if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".md") || lowerFileName.endsWith(".todo")) {
			binding.openWithText.visibility = View.VISIBLE
			binding.openWithText.setOnClickListener {
				callback?.onOpenWithTextFileClicked(cloudFileModel)
				dismiss()
			}
		}

		binding.shareFile.setOnClickListener {
			callback?.onShareFileClicked(cloudFileModel)
			dismiss()
		}
		binding.moveFile.setOnClickListener {
			callback?.onMoveFileClicked(cloudFileModel)
			dismiss()
		}
		binding.exportFile.setOnClickListener {
			callback?.onExportFileClicked(cloudFileModel)
			dismiss()
		}
		// only vault files can be kept: outside a vault there is nothing to decrypt later
		if (cloudFileModel.toCloudNode().cloud is CryptoCloud) {
			binding.keepOffline.visibility = View.VISIBLE
			if (offline) {
				binding.keepOffline.setText(R.string.screen_file_browser_node_action_remove_offline)
				TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(binding.keepOffline, R.drawable.ic_delete, 0, 0, 0)
			}
			binding.keepOffline.setOnClickListener {
				if (offline) callback?.onRemoveOfflineClicked(cloudFileModel) else callback?.onKeepOfflineClicked(cloudFileModel)
				dismiss()
			}
		} else {
			binding.keepOffline.visibility = View.GONE
		}
		binding.renameFile.setOnClickListener {
			callback?.onRenameFileClicked(cloudFileModel)
			dismiss()
		}
		binding.deleteFile.setOnClickListener {
			callback?.onDeleteNodeClicked(cloudFileModel)
			dismiss()
		}
	}

	companion object {

		private const val FILE_ARG = "file"
		private const val PARENT_FOLDER_PATH_ARG = "parentFolderPath"
		private const val OFFLINE_ARG = "offline"
		fun newInstance(cloudFileModel: CloudFileModel, parentFolderPath: String, offline: Boolean): FileSettingsBottomSheet {
			val dialog = FileSettingsBottomSheet()
			val args = Bundle()
			args.putSerializable(FILE_ARG, cloudFileModel)
			args.putString(PARENT_FOLDER_PATH_ARG, parentFolderPath)
			args.putBoolean(OFFLINE_ARG, offline)
			dialog.arguments = args
			return dialog
		}
	}
}
