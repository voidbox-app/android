package org.cryptomator.presentation.ui.fragment

/** The search box of the text editor toolbar, served by whichever fragment shows the text. */
interface TextSearch {

	fun onQueryText(query: String)
	fun onPreviousQuery()
	fun onNextQuery()
}
