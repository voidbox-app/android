package org.cryptomator.presentation.presenter

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.file.FileCacheUtils
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import timber.log.Timber

@PerView
class TextEditorPresenter @Inject constructor( //
	private val fileCacheUtils: FileCacheUtils,  //
	private val fileUtil: FileUtil,  //
	private val contentResolverUtil: ContentResolverUtil,  //
	private val uploadFilesUseCase: UploadFilesUseCase,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<TextEditorView>(exceptionMappings) {

	private val textFile = AtomicReference<CloudFileModel>()
	private lateinit var retainedState: TextEditorRetainedState

	@JvmField
	@InstanceState
	var didLoadFileContent = false

	@JvmField
	@InstanceState
	var lastFilterLocation = 0

	@JvmField
	@InstanceState
	var query: String? = null

	fun onBackPressed() {
		if (hasUnsavedChanges()) {
			view?.showUnsavedChangesDialog()
		} else {
			view?.performBackPressed()
		}
	}

	private fun hasUnsavedChanges(): Boolean {
		val originalContent = retainedState.originalContent ?: return false
		return originalContent != view?.textFileContent
	}

	fun saveChanges() {
		if (!hasUnsavedChanges()) {
			return
		}
		view?.let {
			it.showProgress(ProgressModel.GENERIC)
			val uri = fileCacheUtils.tmpFile() //
				.withContent(it.textFileContent) //
				.create()
			uploadFile(textFile.get().name, UriBasedDataSource.from(uri))
		}
	}

	private fun uploadFile(fileName: String, dataSource: DataSource) {
		textFile.get().parent?.let {
			uploadFilesUseCase //
				.withParent(it.toCloudNode()) //
				.andFiles(
					listOf( //
						UploadFile.anUploadFile() //
							.withFileName(fileName) //
							.withDataSource(dataSource) //
							.thatIsReplacing(true) //
							.build() //
					)
				).run(object : DefaultProgressAwareResultHandler<List<CloudFile?>, UploadState>() {
					override fun onFinished() {
						view?.showProgress(ProgressModel.COMPLETED)
						view?.finish()
						view?.showMessage(R.string.screen_text_editor_save_success)
					}

					override fun onError(e: Throwable) {
						view?.showProgress(ProgressModel.COMPLETED)
						showError(e)
					}
				})
		} ?: throw ParentFolderIsNullException(textFile.get().name)
	}

	fun loadFileContent() {
		when {
			retainedState.isLoaded -> restoreContent()
			didLoadFileContent && !decryptedFileStillReadable() -> closeWithoutContent()
			else -> readFileContent()
		}
	}

	private fun closeWithoutContent() {
		Timber.tag("TextEditorPresenter").i("The decrypted text file is no longer readable, closing the editor")
		view?.finish()
	}

	private fun decryptedFileStillReadable(): Boolean {
		return view?.allVaultsLocked() == false && fileUtil.fileFor(textFile.get()).exists()
	}

	private fun restoreContent() {
		val content = retainedState.editedContent ?: retainedState.originalContent ?: return
		view?.displayTextFileContent(content)
		view?.restoreEditorPosition(retainedState.position)
		retainedState.editedContent = null
	}

	private fun readFileContent() {
		val textFileUri = try {
			fileUtil.contentUriFor(textFile.get())
		} catch (e: IllegalStateException) {
			closeWithoutContent()
			return
		}
		try {
			contentResolverUtil.openInputStream(textFileUri)?.let { data ->
				val content = fileCacheUtils.read(data)
				retainedState.originalContent = content
				view?.displayTextFileContent(content)
				didLoadFileContent = true
			}
		} catch (e: IOException) {
			showError(e)
		}
	}

	fun keepEditorContent(content: CharSequence, position: EditorPosition) {
		if (!retainedState.isLoaded) {
			return
		}
		retainedState.editedContent = content
		retainedState.position = position
	}

	fun setTextFile(textFile: CloudFileModel) {
		this.textFile.set(textFile)
	}

	fun setRetainedState(retainedState: TextEditorRetainedState) {
		this.retainedState = retainedState
	}

	init {
		unsubscribeOnDestroy(uploadFilesUseCase)
	}
}
