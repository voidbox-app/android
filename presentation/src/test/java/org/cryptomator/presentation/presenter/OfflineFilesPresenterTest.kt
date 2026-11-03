package org.cryptomator.presentation.presenter

import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.cryptomator.data.util.OfflineCopies
import org.cryptomator.domain.Vault
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.OfflineVaultModel
import org.cryptomator.presentation.ui.activity.view.OfflineFilesView
import org.cryptomator.presentation.util.FileSizeHelper
import org.cryptomator.presentation.util.OfflineFiles
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OfflineFilesPresenterTest {

	private val view: OfflineFilesView = mock()
	private val getVaultListUseCase: GetVaultListUseCase = mock()
	private val offlineFiles: OfflineFiles = mock()
	private val fileSizeHelper: FileSizeHelper = mock()
	private val exceptionHandlers: ExceptionHandlers = mock()
	private lateinit var presenter: OfflineFilesPresenter

	@BeforeEach
	fun setUp() {
		RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
		RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }
		RxAndroidPlugins.setMainThreadSchedulerHandler { Schedulers.trampoline() }
		presenter = OfflineFilesPresenter(getVaultListUseCase, offlineFiles, fileSizeHelper, exceptionHandlers)
		presenter.view = view
	}

	@AfterEach
	fun tearDown() {
		RxJavaPlugins.reset()
		RxAndroidPlugins.reset()
	}

	@Test
	fun `usage is shown per vault in vault order, with copies of a removed vault last`() {
		whenever(offlineFiles.usage()).thenReturn(
			listOf(OfflineCopies.VaultUsage(7, 10, 1), OfflineCopies.VaultUsage(2, 300, 3), OfflineCopies.VaultUsage(1, 20, 2))
		)

		presenter.loadUsage()
		vaultListResult().onSuccess(listOf(vault(1, "Photos", position = 1), vault(2, "Docs", position = 0)))

		val shown = shownUsage()
		assertEquals(listOf(2L, 1L, 7L), shown.map { it.vaultId })
		assertEquals(listOf("Docs", "Photos"), shown.take(2).map { it.vault?.name })
		assertNull(shown[2].vault)
		assertEquals(listOf(300L, 20L, 10L), shown.map { it.bytes })
		assertEquals(listOf(3, 2, 1), shown.map { it.files })
	}

	@Test
	fun `copies of removed vaults are listed in a stable order`() {
		whenever(offlineFiles.usage()).thenReturn(
			listOf(OfflineCopies.VaultUsage(7, 10, 1), OfflineCopies.VaultUsage(8, 300, 3), OfflineCopies.VaultUsage(9, 20, 2))
		)

		presenter.loadUsage()
		vaultListResult().onSuccess(emptyList())

		assertEquals(listOf(7L, 8L, 9L), shownUsage().map { it.vaultId })
	}

	@Test
	fun `no copies show an empty list`() {
		whenever(offlineFiles.usage()).thenReturn(emptyList())

		presenter.loadUsage()
		vaultListResult().onSuccess(listOf(vault(1, "Photos", position = 0)))

		assertEquals(emptyList<OfflineVaultModel>(), shownUsage())
	}

	@Test
	fun `removing one vault's copies deletes them and reloads the usage`() {
		whenever(offlineFiles.usage()).thenReturn(emptyList())

		presenter.onRemoveConfirmed(OfflineVaultModel(2, null, 300, 3))

		verify(offlineFiles).deleteVault(2)
		verify(offlineFiles, never()).deleteAll()
		verify(view).showMessage(R.string.screen_offline_files_removed)
		verify(getVaultListUseCase).run(any())
	}

	@Test
	fun `removing all copies deletes every vault and reloads the usage`() {
		whenever(offlineFiles.usage()).thenReturn(emptyList())

		presenter.onRemoveConfirmed(null)

		verify(offlineFiles).deleteAll()
		verify(getVaultListUseCase).run(any())
	}

	private fun vault(id: Long, name: String, position: Int): Vault {
		val vault: Vault = mock()
		whenever(vault.id).thenReturn(id)
		whenever(vault.name).thenReturn(name)
		whenever(vault.position).thenReturn(position)
		return vault
	}

	private fun vaultListResult(): ResultHandler<List<Vault>> {
		val captor = argumentCaptor<ResultHandler<List<Vault>>>()
		verify(getVaultListUseCase).run(captor.capture())
		return captor.firstValue
	}

	private fun shownUsage(): List<OfflineVaultModel> {
		val captor = argumentCaptor<List<OfflineVaultModel>>()
		verify(view).showUsage(captor.capture())
		return captor.firstValue
	}
}
