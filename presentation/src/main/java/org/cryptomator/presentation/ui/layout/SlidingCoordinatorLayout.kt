package org.cryptomator.presentation.ui.layout

import android.content.Context
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout

/** Target of the folder slide animators in `res/animator`. */
class SlidingCoordinatorLayout : CoordinatorLayout {

	constructor(context: Context) : super(context)
	constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

	/** -1 is fully off screen to the left, 1 to the right. */
	var xFraction: Float = 0f
		set(value) {
			field = value
			// may be set before the first layout; onSizeChanged applies it
			if (width > 0) {
				translationX = value * width
			}
		}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		translationX = xFraction * w
	}
}
