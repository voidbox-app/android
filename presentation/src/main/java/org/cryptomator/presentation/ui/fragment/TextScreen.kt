package org.cryptomator.presentation.ui.fragment

import org.cryptomator.presentation.model.ProgressModel

/** What the toolbar and the presenter need from whichever fragment shows the text. */
interface TextScreen {

	fun showLoadingProgress(progress: ProgressModel)
	fun hideLoadingProgress()
	fun onQueryText(query: String)
	fun onPreviousQuery()
	fun onNextQuery()
}
