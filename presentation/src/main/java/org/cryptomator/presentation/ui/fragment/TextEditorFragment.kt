package org.cryptomator.presentation.ui.fragment

import android.os.Bundle
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.View
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.FragmentTextEditorBinding
import org.cryptomator.presentation.presenter.EditorPosition
import org.cryptomator.presentation.presenter.TextEditorPresenter
import org.cryptomator.presentation.ui.layout.applySystemBarsMargins
import org.cryptomator.presentation.ui.layout.applySystemBarsPadding
import org.cryptomator.presentation.ui.layout.attachFastScrollThumb
import javax.inject.Inject

@Fragment
class TextEditorFragment : BaseFragment<FragmentTextEditorBinding>(FragmentTextEditorBinding::inflate) {

	@Inject
	lateinit var textEditorPresenter: TextEditorPresenter

	private var fastScrollCleanup: (() -> Unit)? = null
	private var caretAutoScrollWatcher: TextWatcher? = null
	private var pendingPosition: EditorPosition? = null

	val textFileContent: String
		get() = binding.textEditor.text.toString()

	override fun setupView() {
		// no-op
	}

	override fun loadContent() {
		textEditorPresenter.loadFileContent()
	}

	fun displayTextFileContent(textFileContent: CharSequence) {
		caretAutoScrollWatcher?.let { binding.textEditor.removeTextChangedListener(it) }
		binding.textEditor.setText(textFileContent)
		caretAutoScrollWatcher?.let { binding.textEditor.addTextChangedListener(it) }
	}

	fun restoreEditorPosition(position: EditorPosition) {
		val editor = binding.textEditor
		val length = editor.length()
		editor.setSelection(position.selectionStart.coerceIn(0, length), position.selectionEnd.coerceIn(0, length))
		pendingPosition = position
		editor.doOnLayout { applyPendingPosition() }
	}

	private fun applyPendingPosition() {
		val position = pendingPosition ?: return
		pendingPosition = null
		val editor = binding.textEditor
		val layout = editor.layout ?: return
		val length = editor.length()
		val anchorLine = layout.getLineForOffset(position.anchorOffset.coerceIn(0, length))
		val lineTop = editor.paddingTop + layout.getLineTop(anchorLine)
		val lineHeight = layout.getLineBottom(anchorLine) - layout.getLineTop(anchorLine)
		val maxDistance = (heightWithoutBottomInset - lineHeight).coerceAtLeast(0)
		binding.textViewWrapper.scrollTo(0, lineTop - position.anchorDistanceFromTop.coerceIn(0, maxDistance))
		restoreSelectionMovedByFocusRestore(position.selectionStart.coerceIn(0, length), position.selectionEnd.coerceIn(0, length))
	}

	private fun restoreSelectionMovedByFocusRestore(selectionStart: Int, selectionEnd: Int) {
		binding.textEditor.setSelection(selectionStart, selectionEnd)
	}

	fun setReadOnly() {
		binding.textEditor.isFocusable = false
		binding.textEditor.isFocusableInTouchMode = false
		binding.textEditor.isCursorVisible = false
	}

	fun onQueryText(query: String) {
		textEditorPresenter.query = query

		clearSpans(binding.textEditor)

		if (query.isEmpty()) {
			return
		}

		textEditorPresenter.lastFilterLocation = -1

		onNextQuery()
	}

	fun onPreviousQuery() {
		onQuery(Direction.PREVIOUS)
	}

	fun onNextQuery() {
		onQuery(Direction.NEXT)
	}

	private fun onQuery(direction: Direction) {
		if (textEditorPresenter.query == null) {
			return
		}

		clearSpans(binding.textEditor)

		val fulltext = binding.textEditor.text.toString().lowercase()

		textEditorPresenter.query?.lowercase()?.let {
			val index: Int = when (direction) {
				Direction.PREVIOUS -> {
					textEditorPresenter.lastFilterLocation -= 1

					if (textEditorPresenter.lastFilterLocation < 0) {
						return
					}

					fulltext.lastIndexOf(it, textEditorPresenter.lastFilterLocation)
				}
				Direction.NEXT -> {
					textEditorPresenter.lastFilterLocation += 1
					fulltext.indexOf(it, textEditorPresenter.lastFilterLocation)
				}
			}

			if (index < 0) {
				return
			}

			binding.textEditor.text?.setSpan(
				BackgroundColorSpan(ContextCompat.getColor(context(), R.color.colorPrimaryTransparent)),
				index,
				index + it.length,
				Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
			)

			textEditorPresenter.lastFilterLocation = index

			binding.textEditor.setSelection(index, index + it.length)
			binding.textEditor.post { scrollCaretIntoView() }
		}
	}

	private fun clearSpans(@NonNull editable: TextInputEditText) {
		editable.text
			?.getSpans(0, editable.length(), BackgroundColorSpan::class.java)
			?.forEach { span ->
				editable.text?.removeSpan(span)
			}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.textViewWrapper.applySystemBarsPadding(left = true, right = true, bottom = true)
		binding.scrollThumb.applySystemBarsMargins(end = true, bottom = true)
		binding.scrollTrack.applySystemBarsMargins(end = true, bottom = true)
		fastScrollCleanup = binding.textViewWrapper.attachFastScrollThumb(binding.scrollThumb, binding.scrollTrack, binding.textEditor)
		setupCaretAutoScroll()
	}

	override fun onDestroyView() {
		keepEditorContent()
		pendingPosition = null
		fastScrollCleanup?.invoke()
		fastScrollCleanup = null
		caretAutoScrollWatcher?.let { binding.textEditor.removeTextChangedListener(it) }
		caretAutoScrollWatcher = null
		super.onDestroyView()
	}

	private fun keepEditorContent() {
		if (!::textEditorPresenter.isInitialized) {
			return
		}
		val editor = binding.textEditor
		val content = editor.text ?: return
		textEditorPresenter.keepEditorContent(content, editorPosition())
	}

	private fun editorPosition(): EditorPosition {
		val editor = binding.textEditor
		val scroll = binding.textViewWrapper
		val layout = editor.layout ?: return EditorPosition(editor.selectionStart, editor.selectionEnd, 0, 0)
		return if (editor.isFocusable) {
			val caretLine = layout.getLineForOffset(editor.selectionEnd)
			EditorPosition(editor.selectionStart, editor.selectionEnd, editor.selectionEnd, editor.paddingTop + layout.getLineTop(caretLine) - scroll.scrollY)
		} else {
			val firstVisibleLine = layout.getLineForVertical(scroll.scrollY - editor.paddingTop)
			EditorPosition(editor.selectionStart, editor.selectionEnd, layout.getLineStart(firstVisibleLine), 0)
		}
	}

	private val visibleHeight: Int
		get() = binding.textViewWrapper.height - binding.textViewWrapper.paddingTop - binding.textViewWrapper.paddingBottom

	private val heightWithoutBottomInset: Int
		get() = binding.textViewWrapper.height - binding.textViewWrapper.paddingTop

	private fun setupCaretAutoScroll() {
		caretAutoScrollWatcher = binding.textEditor.doAfterTextChanged {
			binding.textEditor.post { scrollCaretIntoView() }
		}
	}

	private fun scrollCaretIntoView() {
		val editor = binding.textEditor
		val scroll = binding.textViewWrapper
		val layout = editor.layout ?: return
		val line = layout.getLineForOffset(editor.selectionEnd)
		val lineTop = editor.paddingTop + layout.getLineTop(line)
		val lineBottom = editor.paddingTop + layout.getLineBottom(line)
		when {
			lineTop < scroll.scrollY -> scroll.smoothScrollTo(0, lineTop)
			lineBottom > scroll.scrollY + visibleHeight -> scroll.smoothScrollTo(0, lineBottom - visibleHeight)
		}
	}

	enum class Direction { PREVIOUS, NEXT }
}
