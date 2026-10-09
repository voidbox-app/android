package org.cryptomator.presentation.presenter

import androidx.lifecycle.ViewModel
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.SingleSubject
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.util.TextPages
import java.io.Closeable

/** Everything about the opened text that must survive a recreation of the screen: the text itself and the work that loads it. */
class TextEditorRetainedState : ViewModel() {

	sealed class LoadedText : Closeable {

		class Editable(val text: String) : LoadedText() {

			override fun close() {}
		}

		class ReadOnly(val pages: TextPages) : LoadedText() {

			override fun close() {
				pages.close()
			}
		}
	}

	var originalContent: String? = null
	var editedContent: CharSequence? = null
	var position = EditorPosition(0, 0, 0, 0)

	var pages: TextPages? = null
		private set
	var currentMatch: TextPages.Match? = null

	val progress: BehaviorSubject<ProgressModel> = BehaviorSubject.createDefault(ProgressModel.GENERIC)
	private val pageCountUpdates: BehaviorSubject<Int> = BehaviorSubject.create()

	private var loading: Disposable? = null
	private var indexing: Disposable? = null
	private var result: SingleSubject<LoadedText>? = null

	val isLoaded: Boolean
		get() = originalContent != null || pages != null

	val loadingResult: Single<LoadedText>?
		get() = result

	/** Runs [load] once in the background; the result waits in [loadingResult] for whichever screen asks for it. */
	fun startLoading(load: () -> LoadedText) {
		loading?.dispose()
		progress.onNext(ProgressModel.GENERIC)
		val subject = SingleSubject.create<LoadedText>()
		result = subject
		loading = Single.create<LoadedText> { emitter ->
			val loaded = try {
				load()
			} catch (e: Exception) {
				emitter.tryOnError(e)
				return@create
			}
			if (emitter.isDisposed) {
				loaded.close()
			} else {
				emitter.onSuccess(loaded)
			}
		}
			.subscribeOn(Schedulers.io())
			.subscribe(subject::onSuccess, subject::onError)
	}

	fun forgetLoading() {
		result = null
		loading = null
	}

	fun keepPages(pages: TextPages) {
		this.pages = pages
		indexing = Completable.fromAction { pages.index(pageCountUpdates::onNext) }
			.subscribeOn(Schedulers.io())
			.subscribe(pageCountUpdates::onComplete, pageCountUpdates::onError)
	}

	fun pageCount(): Observable<Int> = pageCountUpdates

	override fun onCleared() {
		loading?.dispose()
		indexing?.dispose()
		result?.value?.close()
		result = null
		pages?.close()
		pages = null
	}
}
