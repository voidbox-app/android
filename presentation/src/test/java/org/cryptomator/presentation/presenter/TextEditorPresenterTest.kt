package org.cryptomator.presentation.presenter

import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import io.reactivex.schedulers.TestScheduler
import org.cryptomator.domain.repository.RandomAccessContent
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.mappers.ProgressModelMapper
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.util.VaultTextFiles
import org.cryptomator.util.file.FileCacheUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class TextEditorPresenterTest {

	private val view: TextEditorView = mock()
	private val fileCacheUtils: FileCacheUtils = mock()
	private val vaultTextFiles: VaultTextFiles = mock()
	private val uploadFilesUseCase: UploadFilesUseCase = mock()
	private val progressModelMapper: ProgressModelMapper = mock()
	private val exceptionHandlers: ExceptionHandlers = mock()
	private val textFile: CloudFileModel = mock()
	private val retainedState = TextEditorRetainedState()
	private lateinit var presenter: TextEditorPresenter

	@BeforeEach
	fun setUp() {
		RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
		RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
		RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
		whenever(vaultTextFiles.open(eq(textFile), any())).thenAnswer { InMemoryContent(CONTENT) }
		whenever(vaultTextFiles.fitsInEditor(any())).thenReturn(true)
		whenever(view.allVaultsLocked()).thenReturn(false)
		presenter = newPresenter(view)
	}

	@AfterEach
	fun tearDown() {
		RxJavaPlugins.reset()
		RxAndroidPlugins.reset()
	}

	private fun newPresenter(view: TextEditorView): TextEditorPresenter {
		val presenter = TextEditorPresenter(fileCacheUtils, vaultTextFiles, uploadFilesUseCase, progressModelMapper, exceptionHandlers)
		presenter.view = view
		presenter.setTextFile(textFile)
		presenter.setRetainedState(retainedState)
		return presenter
	}

	@Test
	fun `the first open decrypts the text into memory and shows it`() {
		presenter.loadFileContent()

		verify(view).displayTextFileContent(CONTENT)
		verify(view).showProgress(ProgressModel.COMPLETED)
		assertEquals(CONTENT, retainedState.originalContent)
		assertTrue(presenter.didLoadFileContent)
		assertFalse(presenter.isReadOnlyText)
	}

	@Test
	fun `a text too large for the editor opens for reading only`() {
		whenever(vaultTextFiles.fitsInEditor(any())).thenReturn(false)

		presenter.loadFileContent()

		verify(view).showReadOnlyText()
		verify(view, never()).displayTextFileContent(any())
		assertTrue(presenter.isReadOnlyText)
		assertNotNull(presenter.pages)
		assertNull(retainedState.originalContent)
		assertTrue(presenter.didLoadFileContent)
	}

	@Test
	fun `a recreated read only screen shows the pages again without reading the file again`() {
		whenever(vaultTextFiles.fitsInEditor(any())).thenReturn(false)
		presenter.loadFileContent()
		val recreatedView: TextEditorView = mock()
		val recreated = newPresenter(recreatedView)

		recreated.loadFileContent()

		verify(recreatedView).showReadOnlyText()
		verify(vaultTextFiles, times(1)).open(any(), any())
	}

	@Test
	fun `a failed read shows the error and leaves nothing loaded`() {
		whenever(vaultTextFiles.open(eq(textFile), any())).thenThrow(IOException("gone"))

		presenter.loadFileContent()

		verify(exceptionHandlers).handle(eq(view), any())
		verify(view).showProgress(ProgressModel.COMPLETED)
		verify(view, never()).displayTextFileContent(any())
		assertFalse(retainedState.isLoaded)
		assertFalse(presenter.didLoadFileContent)
	}

	@Test
	fun `a load still running when the screen is recreated reaches the new screen only`() {
		val io = TestScheduler()
		RxJavaPlugins.setIoSchedulerHandler { io }
		presenter.loadFileContent()
		presenter.destroy()
		val recreatedView: TextEditorView = mock()
		val recreated = newPresenter(recreatedView)
		recreated.loadFileContent()

		io.triggerActions()

		verify(recreatedView).displayTextFileContent(CONTENT)
		verify(view, never()).displayTextFileContent(any())
		verify(vaultTextFiles, times(1)).open(any(), any())
	}

	@Test
	fun `a result arriving while the screen is paused waits for the resume`() {
		val io = TestScheduler()
		RxJavaPlugins.setIoSchedulerHandler { io }
		presenter.loadFileContent()
		presenter.pause()

		io.triggerActions()
		verify(view, never()).displayTextFileContent(any())
		presenter.resume()

		verify(view).displayTextFileContent(CONTENT)
	}

	@Test
	fun `a recreated screen shows the kept text at the kept position without reading the file again`() {
		presenter.loadFileContent()
		presenter.keepEditorContent("edited", POSITION)

		presenter.loadFileContent()

		verify(view).displayTextFileContent("edited")
		verify(view).restoreEditorPosition(POSITION)
		verify(vaultTextFiles, times(1)).open(any(), any())
		assertNull(retainedState.editedContent)
	}

	@Test
	fun `a recreated screen without kept text shows the original again`() {
		presenter.loadFileContent()

		presenter.loadFileContent()

		verify(view, times(2)).displayTextFileContent(CONTENT)
		verify(vaultTextFiles, times(1)).open(any(), any())
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
	fun `a screen recreated after process death with the vault locked closes without reading`() {
		presenter.didLoadFileContent = true
		whenever(view.allVaultsLocked()).thenReturn(true)

		presenter.loadFileContent()

		verify(view).finish()
		verify(vaultTextFiles, never()).open(any(), any())
		verify(view, never()).displayTextFileContent(any())
	}

	@Test
	fun `a screen recreated after process death with the vault open reads the file again`() {
		presenter.didLoadFileContent = true

		presenter.loadFileContent()

		verify(view).displayTextFileContent(CONTENT)
		verify(view, never()).finish()
	}

	@Test
	fun `editor text is not kept for a read only text`() {
		whenever(vaultTextFiles.fitsInEditor(any())).thenReturn(false)
		presenter.loadFileContent()

		presenter.keepEditorContent("", POSITION)

		assertNull(retainedState.editedContent)
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
	fun `back from a read only text leaves the screen`() {
		whenever(vaultTextFiles.fitsInEditor(any())).thenReturn(false)
		presenter.loadFileContent()
		whenever(view.textFileContent).thenReturn("")

		presenter.onBackPressed()

		verify(view).performBackPressed()
		verify(view, never()).showUnsavedChangesDialog()
	}

	@Test
	fun `saving without changes uploads nothing`() {
		presenter.loadFileContent()
		whenever(view.textFileContent).thenReturn(CONTENT)

		presenter.saveChanges()

		verify(uploadFilesUseCase, never()).withParent(any())
		verify(fileCacheUtils, never()).tmpFile()
	}

	private class InMemoryContent(text: String) : RandomAccessContent {

		private val bytes = text.toByteArray(StandardCharsets.UTF_8)

		override val size: Long = bytes.size.toLong()

		override fun openStream(offset: Long, length: Long?): InputStream {
			val end = if (length == null) bytes.size else minOf(bytes.size.toLong(), offset + length).toInt()
			return ByteArrayInputStream(bytes, offset.toInt(), end - offset.toInt())
		}

		override fun close() {}
	}

	companion object {

		private const val CONTENT = "first line\nsecond line"
		private val POSITION = EditorPosition(2, 4, 4, 120)
	}
}
