package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.presenter.EditorPosition

interface TextEditorView : View {

	val textFileContent: String

	fun allVaultsLocked(): Boolean
	fun performBackPressed()
	fun showUnsavedChangesDialog()
	fun displayTextFileContent(textFileContent: CharSequence)
	fun restoreEditorPosition(position: EditorPosition)

}
