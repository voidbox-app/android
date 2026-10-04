package org.cryptomator.presentation.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.color.MaterialColors
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ItemVaultBinding
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.model.comparator.VaultPositionComparator
import org.cryptomator.presentation.ui.adapter.VaultsAdapter.VaultViewHolder
import javax.inject.Inject

class VaultsAdapter @Inject
internal constructor() : RecyclerViewBaseAdapter<VaultModel, VaultsAdapter.OnItemInteractionListener, VaultViewHolder, ItemVaultBinding>(VaultPositionComparator()), VaultsMoveListener.Listener {

	interface OnItemInteractionListener {

		fun onVaultClicked(vaultModel: VaultModel)

		fun onVaultSettingsClicked(vaultModel: VaultModel)

		fun onVaultLockClicked(vaultModel: VaultModel)

		fun onRowMoved(fromPosition: Int, toPosition: Int)

		fun onVaultMoved(fromPosition: Int, toPosition: Int)
	}

	override fun getItemBinding(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): ItemVaultBinding {
		return ItemVaultBinding.inflate(inflater, parent, false)
	}

	override fun createViewHolder(binding: ItemVaultBinding, viewType: Int): VaultViewHolder {
		return VaultViewHolder(binding)
	}

	fun deleteVault(vaultID: Long) {
		deleteItem(getVault(vaultID))
		notifyItemRangeChanged(0, itemCount)
	}

	fun addOrUpdateVault(vault: VaultModel?) {
		if (contains(vault)) {
			replaceItem(vault)
		} else {
			addItem(vault)
		}
		notifyItemRangeChanged(0, itemCount)
	}

	private fun getVault(vaultId: Long): VaultModel? {
		return itemCollection.firstOrNull { it.vaultId == vaultId }
	}

	inner class VaultViewHolder(private val binding: ItemVaultBinding) : RecyclerViewBaseAdapter<VaultModel, VaultsAdapter.OnItemInteractionListener, VaultViewHolder, ItemVaultBinding>.ItemViewHolder(binding.root) {

		override fun bind(position: Int) {
			val vaultModel = getItem(position)

			binding.vaultName.text = vaultModel.name
			binding.vaultPath.text = vaultModel.path
			bindLeadingIcon(vaultModel)
			bindGroupShape(position)

			binding.unlockedImage.visibility = if (vaultModel.isLocked) View.GONE else View.VISIBLE

			itemView.setOnClickListener { callback.onVaultClicked(vaultModel) }

			binding.unlockedImage.setOnClickListener { callback.onVaultLockClicked(vaultModel) }

			binding.settings.setOnClickListener { callback.onVaultSettingsClicked(vaultModel) }
		}

		/** Locked: the cloud icon on a neutral container. Unlocked: an open lock on primaryContainer. */
		private fun bindLeadingIcon(vaultModel: VaultModel) {
			if (vaultModel.isLocked) {
				binding.cloudImage.setImageResource(vaultModel.cloudImageResource)
				// brand logos keep their own colors, symbols take the theme's
				binding.cloudImage.imageTintList = if (vaultModel.cloudImageResource == R.drawable.nextcloud) null else themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
				binding.cloudImageContainer.backgroundTintList = themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
			} else {
				binding.cloudImage.setImageResource(R.drawable.ic_lock_open_filled)
				binding.cloudImage.imageTintList = themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer)
				binding.cloudImageContainer.backgroundTintList = themeColor(com.google.android.material.R.attr.colorPrimaryContainer)
			}
		}

		/** Rows form one group: 20dp corners at the group's ends, 4dp between neighbours. */
		private fun bindGroupShape(position: Int) {
			val card = binding.root
			val outer = card.resources.getDimension(R.dimen.list_group_corner)
			val inner = card.resources.getDimension(R.dimen.list_group_inner_corner)
			val top = if (position == 0) outer else inner
			val bottom = if (position == itemCount - 1) outer else inner
			card.shapeAppearanceModel = card.shapeAppearanceModel.toBuilder() //
				.setTopLeftCornerSize(top).setTopRightCornerSize(top) //
				.setBottomLeftCornerSize(bottom).setBottomRightCornerSize(bottom) //
				.build()
		}

		private fun themeColor(attr: Int): ColorStateList {
			return ColorStateList.valueOf(MaterialColors.getColor(binding.root, attr))
		}
	}

	override fun onVaultMoved(fromPosition: Int, toPosition: Int) {
		callback.onVaultMoved(fromPosition, toPosition)
	}

	override fun onRowMoved(fromPosition: Int, toPosition: Int) {
		callback.onRowMoved(fromPosition, toPosition)
	}
}
