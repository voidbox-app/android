package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.file.FileCacheUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class TextEditorPresenterTest {

	private val view: TextEditorView = mock()
	private val fileCacheUtils: FileCacheUtils = mock()
	private val fileUtil: FileUtil = mock()
	private val contentResolverUtil: ContentResolverUtil = mock()
	private val uploadFilesUseCase: UploadFilesUseCase = mock()
	private val exceptionHandlers: ExceptionHandlers = mock()
	private val textFile: CloudFileModel = mock()
	private val textFileUri: Uri = mock()
	private val decryptedFile: File = mock()
	private val retainedState = TextEditorRetainedState()
	private lateinit var presenter: TextEditorPresenter

	@BeforeEach
	fun setUp() {
		whenever(fileUtil.contentUriFor(textFile)).thenReturn(textFileUri)
		whenever(fileUtil.fileFor(textFile)).thenReturn(decryptedFile)
		whenever(decryptedFile.exists()).thenReturn(true)
		whenever(contentResolverUtil.openInputStream(textFileUri)).thenReturn(ByteArrayInputStream(ByteArray(0)))
		whenever(fileCacheUtils.read(any())).thenReturn(CONTENT)
		whenever(view.allVaultsLocked()).thenReturn(false)
		presenter = TextEditorPresenter(fileCacheUtils, fileUtil, contentResolverUtil, uploadFilesUseCase, exceptionHandlers)
		presenter.view = view
		presenter.setTextFile(textFile)
		presenter.setRetainedState(retainedState)
	}

	@Test
	fun `the first open reads the decrypted file and shows it`() {
		presenter.loadFileContent()

		verify(view).displayTextFileContent(CONTENT)
		assertEquals(CONTENT, retainedState.originalContent)
		assertTrue(presenter.didLoadFileContent)
	}

	@Test
	fun `a failed read shows the error and leaves nothing loaded`() {
		whenever(fileCacheUtils.read(any())).thenThrow(IOException("gone"))

		presenter.loadFileContent()

		verify(exceptionHandlers).handle(eq(view), any())
		verify(view, never()).displayTextFileContent(any())
		assertFalse(retainedState.isLoaded)
		assertFalse(presenter.didLoadFileContent)
	}

	@Test
	fun `a missing decrypted file on the first open closes the editor`() {
		whenever(fileUtil.contentUriFor(textFile)).thenThrow(IllegalStateException("missing"))

		presenter.loadFileContent()

		verify(view).finish()
		verify(view, never()).displayTextFileContent(any())
	}

	@Test
	fun `a recreated screen shows the kept text at the kept position without reading the file again`() {
		presenter.loadFileContent()
		presenter.keepEditorContent("edited", POSITION)

		presenter.loadFileContent()

		verify(view).displayTextFileContent("edited")
		verify(view).restoreEditorPosition(POSITION)
		verify(fileCacheUtils, times(1)).read(any())
		assertNull(retainedState.editedContent)
	}

	@Test
	fun `a recreated screen without kept text shows the original again`() {
		presenter.loadFileContent()

		presenter.loadFileContent()

		verify(view, times(2)).displayTextFileContent(CONTENT)
		verify(fileCacheUtils, times(1)).read(any())
	}

	@Test
	fun `edits kept across a recreation still count as unsaved`() {
		presenter.loadFileContent()
		presenter.keepEditorContent("edited", POSITION)
		presenter.loadFileContent()
		whenever(view.textFileContent).thenReturn("edited")

		presenter.onBackPressed()

		verify(view).showUnsavedChangesDialog()
	}

	@Test
	fun `a screen recreated after process death closes without touching the decrypted file`() {
		presenter.didLoadFileContent = true
		whenever(view.allVaultsLocked()).thenReturn(true)

		presenter.loadFileContent()

		verify(view).finish()
		verify(fileUtil, never()).fileFor(any())
		verify(fileUtil, never()).contentUriFor(any())
		verify(view, never()).displayTextFileContent(any())
	}

	@Test
	fun `a screen recreated with the vault open and the file present reads the file again`() {
		presenter.didLoadFileContent = true

		presenter.loadFileContent()

		verify(view).displayTextFileContent(CONTENT)
		verify(view, never()).finish()
	}

	@Test
	fun `a screen recreated with the vault open but the file gone closes`() {
		presenter.didLoadFileContent = true
		whenever(decryptedFile.exists()).thenReturn(false)

		presenter.loadFileContent()

		verify(view).finish()
		verify(fileUtil, never()).contentUriFor(any())
	}

	@Test
	fun `text of a screen that never loaded is not kept`() {
		presenter.keepEditorContent("typed", POSITION)

		assertNull(retainedState.editedContent)
	}

	@Test
	fun `back with the original text leaves the screen`() {
		presenter.loadFileContent()
		whenever(view.textFileContent).thenReturn(CONTENT)

		presenter.onBackPressed()

		verify(view).performBackPressed()
		verify(view, never()).showUnsavedChangesDialog()
	}

	@Test
	fun `back with changed text asks about the unsaved changes`() {
		presenter.loadFileContent()
		whenever(view.textFileContent).thenReturn("changed")

		presenter.onBackPressed()

		verify(view).showUnsavedChangesDialog()
		verify(view, never()).performBackPressed()
	}

	@Test
	fun `back before the content was loaded leaves the screen`() {
		whenever(view.textFileContent).thenReturn("")

		presenter.onBackPressed()

		verify(view).performBackPressed()
	}

	@Test
	fun `saving without changes uploads nothing`() {
		presenter.loadFileContent()
		whenever(view.textFileContent).thenReturn(CONTENT)

		presenter.saveChanges()

		verify(uploadFilesUseCase, never()).withParent(any())
		verify(view, never()).showProgress(any())
	}

	companion object {

		private const val CONTENT = "first line\nsecond line"
		private val POSITION = EditorPosition(2, 4, 4, 120)
	}
}
