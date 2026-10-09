package org.cryptomator.presentation.presenter

import androidx.lifecycle.ViewModel

class TextEditorRetainedState : ViewModel() {

	var originalContent: String? = null
	var editedContent: CharSequence? = null
	var position = EditorPosition(0, 0, 0, 0)

	val isLoaded: Boolean
		get() = originalContent != null
}
