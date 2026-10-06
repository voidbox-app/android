package org.cryptomator.presentation.ui.layout

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout

/**
 * Root of the file list: the folder animators in `res/animator` move it by a fraction of its
 * width, so that a list slides in from one side while the previous one slides out to the other.
 */
class SlidingCoordinatorLayout : CoordinatorLayout {

	constructor(context: Context) : super(context)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

	/** Horizontal offset as a fraction of the width: -1 is fully off screen to the left, 1 to the right. */
	var xFraction: Float = 0f
		set(value) {
			field = value
			// the animator may start before the first layout; onSizeChanged applies the value then
			if (width > 0) {
				translationX = value * width
			}
		}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		translationX = xFraction * w
	}
}
