package org.cryptomator.presentation.presenter

data class EditorPosition(
	val selectionStart: Int,
	val selectionEnd: Int,
	val anchorOffset: Int,
	val anchorDistanceFromTop: Int
)
