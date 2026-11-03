package org.cryptomator.presentation.presenter

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.cryptomator.domain.Vault
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.OfflineVaultModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.activity.view.OfflineFilesView
import org.cryptomator.presentation.ui.dialog.RemoveOfflineFilesDialog
import org.cryptomator.presentation.util.FileSizeHelper
import org.cryptomator.presentation.util.OfflineFiles
import javax.inject.Inject

@PerView
class OfflineFilesPresenter @Inject constructor(
	private val getVaultListUseCase: GetVaultListUseCase, //
	private val offlineFiles: OfflineFiles, //
	private val fileSizeHelper: FileSizeHelper, //
	exceptionMappings: ExceptionHandlers
) : Presenter<OfflineFilesView>(exceptionMappings) {

	private val work = CompositeDisposable()
	private var shown: List<OfflineVaultModel> = emptyList()

	fun loadUsage() {
		getVaultListUseCase.run(object : DefaultResultHandler<List<Vault>>() {
			override fun onSuccess(vaults: List<Vault>) {
				showUsage(vaults)
			}
		})
	}

	private fun showUsage(vaults: List<Vault>) {
		work.add(Single.fromCallable { usageOf(vaults) } //
			.subscribeOn(Schedulers.io()) //
			.observeOn(AndroidSchedulers.mainThread()) //
			.subscribe({
				shown = it
				view?.showUsage(it)
			}, { showError(it) }))
	}

	private fun usageOf(vaults: List<Vault>): List<OfflineVaultModel> {
		val vaultsById = vaults.associateBy { it.id }
		return offlineFiles.usage() //
			.map { usage -> OfflineVaultModel(usage.vaultId, vaultsById[usage.vaultId]?.let { VaultModel(it) }, usage.bytes, usage.files) } //
			.sortedWith(compareBy({ it.vault == null }, { it.vault?.position ?: 0 }, { it.vaultId }))
	}

	fun onRemoveClicked(vault: OfflineVaultModel) {
		view?.showDialog(RemoveOfflineFilesDialog.newInstance(vault, formatted(vault.bytes)))
	}

	fun onRemoveAllClicked() {
		view?.showDialog(RemoveOfflineFilesDialog.newInstance(null, formatted(shown.sumOf { it.bytes })))
	}

	private fun formatted(bytes: Long): String = fileSizeHelper.getFormattedFileSize(bytes) ?: ""

	fun onRemoveConfirmed(vault: OfflineVaultModel?) {
		work.add(Completable.fromAction { if (vault == null) offlineFiles.deleteAll() else offlineFiles.deleteVault(vault.vaultId) } //
			.subscribeOn(Schedulers.io()) //
			.observeOn(AndroidSchedulers.mainThread()) //
			.subscribe({
				view?.showMessage(R.string.screen_offline_files_removed)
				loadUsage()
			}, { showError(it) }))
	}

	override fun destroyed() {
		work.dispose()
	}

	init {
		unsubscribeOnDestroy(getVaultListUseCase)
	}
}
