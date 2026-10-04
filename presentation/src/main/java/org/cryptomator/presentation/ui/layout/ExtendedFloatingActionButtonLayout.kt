package org.cryptomator.presentation.ui.layout

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class ExtendedFloatingActionButtonLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = com.google.android.material.R.attr.extendedFloatingActionButtonStyle
) : ExtendedFloatingActionButton(context, attrs, defStyleAttr) {

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		applySystemBarsMargins(end = true, bottom = true)
	}
}
