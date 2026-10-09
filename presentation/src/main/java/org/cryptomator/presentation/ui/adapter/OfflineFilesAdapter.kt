package org.cryptomator.presentation.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ItemOfflineVaultBinding
import org.cryptomator.presentation.model.OfflineVaultModel
import org.cryptomator.presentation.ui.adapter.OfflineFilesAdapter.OfflineVaultViewHolder
import org.cryptomator.presentation.util.FileSizeHelper
import javax.inject.Inject

class OfflineFilesAdapter @Inject constructor(private val fileSizeHelper: FileSizeHelper) :
	RecyclerViewBaseAdapter<OfflineVaultModel, OfflineFilesAdapter.Callback, OfflineVaultViewHolder, ItemOfflineVaultBinding>() {

	interface Callback {

		fun onRemoveClicked(vault: OfflineVaultModel)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemOfflineVaultBinding {
		return ItemOfflineVaultBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemOfflineVaultBinding, viewType: Int): OfflineVaultViewHolder {
		return OfflineVaultViewHolder(binding)
	}

	inner class OfflineVaultViewHolder(private val binding: ItemOfflineVaultBinding) : RecyclerViewBaseAdapter<OfflineVaultModel, Callback, OfflineVaultViewHolder, ItemOfflineVaultBinding>.ItemViewHolder(binding.root) {

		override fun bind(position: Int) {
			val item = getItem(position)
			val resources = binding.root.resources
			val vault = item.vault
			binding.vaultName.text = vault?.name ?: resources.getString(R.string.screen_offline_files_removed_vault)
			binding.cloudImage.setImageResource(vault?.cloudImageResource ?: R.drawable.ic_cloud)
			binding.usage.text = resources.getString(
				R.string.screen_offline_files_usage, //
				fileSizeHelper.getFormattedFileSize(item.bytes), //
				resources.getQuantityString(R.plurals.screen_offline_files_count, item.files, item.files)
			)
			binding.locked.visibility = if (vault?.isLocked == true) View.VISIBLE else View.GONE
			binding.remove.setOnClickListener { callback?.onRemoveClicked(item) }
		}
	}
}
