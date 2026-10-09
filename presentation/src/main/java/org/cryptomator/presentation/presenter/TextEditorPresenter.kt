package org.cryptomator.presentation.presenter

import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
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
import org.cryptomator.presentation.model.mappers.ProgressModelMapper
import org.cryptomator.presentation.presenter.TextEditorRetainedState.LoadedText
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.util.TextPages
import org.cryptomator.presentation.util.VaultTextFiles
import org.cryptomator.util.file.FileCacheUtils
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import timber.log.Timber

@PerView
class TextEditorPresenter @Inject constructor( //
	private val fileCacheUtils: FileCacheUtils,  //
	private val vaultTextFiles: VaultTextFiles,  //
	private val uploadFilesUseCase: UploadFilesUseCase,  //
	private val progressModelMapper: ProgressModelMapper,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<TextEditorView>(exceptionMappings) {

	private val textFile = AtomicReference<CloudFileModel>()
	private lateinit var retainedState: TextEditorRetainedState
	private val subscriptions = CompositeDisposable()
	private var pageSearch: Disposable? = null
	private var resultWaitingForResume: LoadedText? = null
	private var errorWaitingForResume: Throwable? = null

	@JvmField
	@InstanceState
	var didLoadFileContent = false

	@JvmField
	@InstanceState
	var lastFilterLocation = 0

	@JvmField
	@InstanceState
	var query: String? = null

	val pages: TextPages?
		get() = retainedState.pages

	val isReadOnlyText: Boolean
		get() = retainedState.pages != null

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
			retainedState.loadingResult != null -> observeLoading()
			didLoadFileContent && view?.allVaultsLocked() != false -> closeWithoutContent()
			else -> startLoading()
		}
	}

	private fun closeWithoutContent() {
		Timber.tag("TextEditorPresenter").i("The vault is locked, closing the editor without reading the file again")
		view?.finish()
	}

	private fun restoreContent() {
		if (retainedState.pages != null) {
			view?.showReadOnlyText()
			return
		}
		val content = retainedState.editedContent ?: retainedState.originalContent ?: return
		view?.displayTextFileContent(content)
		view?.restoreEditorPosition(retainedState.position)
		retainedState.editedContent = null
	}

	private fun startLoading() {
		val file = textFile.get()
		retainedState.startLoading {
			val content = vaultTextFiles.open(file) { progress -> retainedState.progress.onNext(progressModelMapper.toModel(progress)) }
			if (vaultTextFiles.fitsInEditor(content.size)) {
				content.use { LoadedText.Editable(it.openStream(0, null).use { stream -> String(stream.readBytes(), StandardCharsets.UTF_8) }) }
			} else {
				LoadedText.ReadOnly(TextPages(content))
			}
		}
		observeLoading()
	}

	private fun observeLoading() {
		val result = retainedState.loadingResult ?: return
		subscriptions.add(
			retainedState.progress //
				.observeOn(AndroidSchedulers.mainThread()) //
				.subscribe { showProgress(it) }
		)
		subscriptions.add(
			result //
				.observeOn(AndroidSchedulers.mainThread()) //
				.subscribe({ showLoadedWhenResumed(it) }, { showLoadingErrorWhenResumed(it) })
		)
	}

	private fun showLoadedWhenResumed(loaded: LoadedText) {
		if (isPaused) {
			resultWaitingForResume = loaded
		} else {
			showLoaded(loaded)
		}
	}

	private fun showLoadingErrorWhenResumed(e: Throwable) {
		if (isPaused) {
			errorWaitingForResume = e
		} else {
			showLoadingError(e)
		}
	}

	override fun resumed() {
		resultWaitingForResume?.let {
			resultWaitingForResume = null
			showLoaded(it)
		}
		errorWaitingForResume?.let {
			errorWaitingForResume = null
			showLoadingError(it)
		}
	}

	private fun showLoaded(loaded: LoadedText) {
		subscriptions.clear()
		retainedState.forgetLoading()
		view?.showProgress(ProgressModel.COMPLETED)
		didLoadFileContent = true
		when (loaded) {
			is LoadedText.Editable -> {
				retainedState.originalContent = loaded.text
				view?.displayTextFileContent(loaded.text)
			}
			is LoadedText.ReadOnly -> {
				retainedState.keepPages(loaded.pages)
				view?.showReadOnlyText()
			}
		}
	}

	private fun showLoadingError(e: Throwable) {
		subscriptions.clear()
		retainedState.forgetLoading()
		view?.showProgress(ProgressModel.COMPLETED)
		showError(e)
	}

	fun pageCount() = retainedState.pageCount()

	fun loadPage(page: Int, onLoaded: (String) -> Unit) {
		val pages = retainedState.pages ?: return
		subscriptions.add(
			Single.fromCallable { pages.page(page) } //
				.subscribeOn(Schedulers.io()) //
				.observeOn(AndroidSchedulers.mainThread()) //
				.subscribe({ onLoaded(it) }, { showError(it) })
		)
	}

	/** Calls [onDone] with the match, or with null when there is none or the search failed; a new search replaces a running one. */
	fun findInPages(forward: Boolean, onDone: (TextPages.Match?) -> Unit) {
		val pages = retainedState.pages ?: return
		val query = query?.takeIf { it.isNotEmpty() } ?: return
		val from = retainedState.currentMatch
		pageSearch?.dispose()
		pageSearch = Single.fromCallable { pages.find(query, from, forward) } //
			.subscribeOn(Schedulers.io()) //
			.observeOn(AndroidSchedulers.mainThread()) //
			.subscribe({ match ->
				retainedState.currentMatch = match ?: from
				onDone(match)
			}, { e ->
				showError(e)
				onDone(null)
			})
	}

	fun startNewPageSearch(query: String) {
		this.query = query
		retainedState.currentMatch = null
	}

	val currentMatch: TextPages.Match?
		get() = retainedState.currentMatch

	fun keepEditorContent(content: CharSequence, position: EditorPosition) {
		if (retainedState.originalContent == null) {
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

	override fun destroyed() {
		subscriptions.clear()
		pageSearch?.dispose()
	}

	init {
		unsubscribeOnDestroy(uploadFilesUseCase)
	}
}
