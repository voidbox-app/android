package org.cryptomator.presentation.ui.layout

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import org.cryptomator.presentation.R

/**
 * Draws Material 3 list groups behind the preference rows: the rows of one category share a
 * surfaceContainer block with 20dp corners at its ends, 4dp corners and 2dp gaps between rows.
 */
class PreferenceGroupDecoration(private val screen: PreferenceScreen, view: View) : RecyclerView.ItemDecoration() {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainer)
	}
	private val outer = view.resources.getDimension(R.dimen.list_group_corner)
	private val inner = view.resources.getDimension(R.dimen.list_group_inner_corner)
	private val gap = view.resources.displayMetrics.density
	private val rect = RectF()
	private val path = Path()

	override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
		val rows = visibleRows()
		for (i in 0 until parent.childCount) {
			val child = parent.getChildAt(i)
			val position = parent.getChildAdapterPosition(child)
			if (position == RecyclerView.NO_POSITION || position >= rows.size || rows[position] is PreferenceCategory) {
				continue
			}
			val top = if (isGroupEdge(rows, position - 1)) outer else inner
			val bottom = if (isGroupEdge(rows, position + 1)) outer else inner
			rect.set(child.left.toFloat(), child.top + gap, child.right.toFloat(), child.bottom - gap)
			path.reset()
			path.addRoundRect(rect, floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom), Path.Direction.CW)
			canvas.drawPath(path, paint)
		}
	}

	private fun isGroupEdge(rows: List<Preference>, position: Int): Boolean {
		return position < 0 || position >= rows.size || rows[position] is PreferenceCategory
	}

	// The adapter lists the visible preferences in screen order, categories first, then their children.
	private fun visibleRows(): List<Preference> {
		val rows = mutableListOf<Preference>()
		fun collect(group: PreferenceGroup) {
			for (i in 0 until group.preferenceCount) {
				val preference = group.getPreference(i)
				if (!preference.isVisible) continue
				rows.add(preference)
				if (preference is PreferenceGroup && preference !is PreferenceScreen) collect(preference)
			}
		}
		collect(screen)
		return rows
	}
}
