package org.cryptomator.presentation.presenter

import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.TestScheduler
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.GetCloudListUseCase
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.ui.activity.view.BrowseFilesView
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit

class BrowseFilesPresenterTest {

	private val scheduler = TestScheduler()
	private val view: BrowseFilesView = mock()
	private val getCloudListUseCase: GetCloudListUseCase = mock()
	private val listing: GetCloudListUseCase.Launcher = mock()
	private val folder: CloudFolderModel = mock()
	private val subfolder: CloudFolderModel = mock()
	private lateinit var presenter: BrowseFilesPresenter

	@BeforeEach
	fun setUp() {
		RxAndroidPlugins.setInitMainThreadSchedulerHandler { scheduler }
		RxAndroidPlugins.setMainThreadSchedulerHandler { scheduler }
		`when`(getCloudListUseCase.withFolder(any())).thenReturn(listing)
		`when`(view.activity()).thenReturn(mock())
		presenter = BrowseFilesPresenter(
			getCloudListUseCase, mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(),
			mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(),
			mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock(),
			mock(), mock(), mock(), mock(), mock(), mock(), mock(), mock()
		)
		presenter.view = view
	}

	@AfterEach
	fun tearDown() {
		RxAndroidPlugins.reset()
	}

	@Test
	fun `a listing that arrives within the delay shows no loading indicator`() {
		presenter.onFolderDisplayed(folder)
		scheduler.advanceTimeBy(400, TimeUnit.MILLISECONDS)
		listingResult().onSuccess(emptyList())
		scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

		verify(view, never()).showLoading(true)
	}

	@Test
	fun `a listing slower than the delay shows the loading indicator until it arrives`() {
		presenter.onFolderDisplayed(folder)
		scheduler.advanceTimeBy(499, TimeUnit.MILLISECONDS)
		verify(view, never()).showLoading(true)

		scheduler.advanceTimeBy(1, TimeUnit.MILLISECONDS)
		listingResult().onSuccess(emptyList())

		val order = inOrder(view)
		order.verify(view).showLoading(true)
		order.verify(view).showLoading(false)
	}

	@Test
	fun `a listing that fails within the delay shows no loading indicator`() {
		presenter.onFolderDisplayed(folder)
		scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS)
		listingResult().onError(RuntimeException())
		scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

		verify(view, never()).showLoading(true)
	}

	@Test
	fun `Back within the delay shows no loading indicator`() {
		presenter.onFolderDisplayed(folder)
		scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS)
		presenter.onBackPressed()
		scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

		verify(view, never()).showLoading(true)
	}

	@Test
	fun `Back hides a shown loading indicator`() {
		presenter.onFolderDisplayed(folder)
		scheduler.advanceTimeBy(600, TimeUnit.MILLISECONDS)
		presenter.onBackPressed()

		val order = inOrder(view)
		order.verify(view).showLoading(true)
		order.verify(view).showLoading(false)
	}

	@Test
	fun `opening a subfolder within the delay shows no loading indicator`() {
		presenter.onFolderDisplayed(folder)
		scheduler.advanceTimeBy(300, TimeUnit.MILLISECONDS)
		presenter.onFolderClicked(subfolder)
		scheduler.advanceTimeBy(1, TimeUnit.SECONDS)

		verify(view, never()).showLoading(true)
	}

	@Test
	fun `opening a subfolder hides a shown loading indicator`() {
		presenter.onFolderDisplayed(folder)
		scheduler.advanceTimeBy(600, TimeUnit.MILLISECONDS)
		presenter.onFolderClicked(subfolder)

		val order = inOrder(view)
		order.verify(view).showLoading(true)
		order.verify(view).showLoading(false)
		order.verify(view).navigateTo(subfolder)
	}

	@Suppress("UNCHECKED_CAST")
	private fun listingResult(): ResultHandler<List<CloudNode>> {
		val captor = ArgumentCaptor.forClass(ResultHandler::class.java) as ArgumentCaptor<ResultHandler<List<CloudNode>>>
		verify(listing).run(captor.capture())
		return captor.value
	}
}
