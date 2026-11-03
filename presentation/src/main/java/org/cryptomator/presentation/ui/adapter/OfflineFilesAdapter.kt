package org.cryptomator.presentation.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.CornerFamily
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
			val context = binding.root.context
			val resources = context.resources
			val vault = item.vault
			val locked = vault?.isLocked == true
			val name = vault?.name ?: resources.getString(R.string.screen_offline_files_removed_vault)

			binding.card.shapeAppearanceModel = binding.card.shapeAppearanceModel.toBuilder()
				.setTopLeftCorner(CornerFamily.ROUNDED, cornerSize(position == 0))
				.setTopRightCorner(CornerFamily.ROUNDED, cornerSize(position == 0))
				.setBottomLeftCorner(CornerFamily.ROUNDED, cornerSize(position == itemCount - 1))
				.setBottomRightCorner(CornerFamily.ROUNDED, cornerSize(position == itemCount - 1))
				.build()
			binding.vaultName.text = name
			binding.cloudImage.setImageResource(vault?.cloudImageResource ?: R.drawable.ic_cloud)
			binding.locked.visibility = if (locked) View.VISIBLE else View.GONE
			binding.dot.backgroundTintList = ColorStateList.valueOf(segmentColor(binding.root, position))
			binding.usage.text = listOfNotNull(
				fileSizeHelper.getFormattedFileSize(item.bytes), //
				resources.getQuantityString(R.plurals.screen_offline_files_count, item.files, item.files), //
				if (locked) resources.getString(R.string.screen_offline_files_locked) else null
			).joinToString(SEPARATOR)
			binding.remove.contentDescription = resources.getString(R.string.screen_offline_files_remove_of, name)
			binding.remove.setOnClickListener { callback?.onRemoveClicked(item) }
		}

		private fun cornerSize(outer: Boolean): Float {
			val density = binding.root.resources.displayMetrics.density
			return (if (outer) 20 else 4) * density
		}
	}

	companion object {

		// a resource would lose the surrounding spaces
		private const val SEPARATOR = " \u00B7 "

		// theme roles, so the shares follow Material You like the rest of the app; neighbours differ in hue
		private val SEGMENT_ROLES = intArrayOf(
			com.google.android.material.R.attr.colorPrimary, //
			com.google.android.material.R.attr.colorTertiary, //
			com.google.android.material.R.attr.colorSecondary, //
			com.google.android.material.R.attr.colorOnTertiaryContainer, //
			com.google.android.material.R.attr.colorOnPrimaryContainer
		)

		/** The colour of a vault's share in the usage bar and of the dot in its row. */
		fun segmentColor(view: View, position: Int): Int = MaterialColors.getColor(view, SEGMENT_ROLES[position % SEGMENT_ROLES.size])
	}
}
