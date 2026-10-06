package org.cryptomator.presentation.util

import android.content.Context
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.repository.DispatchingCloudContentRepository
import org.cryptomator.data.util.OfflineCopies
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.presentation.model.CloudFileModel
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault files kept on the device as the cloud holds them, encrypted. The player, the thumbnails
 * and exports then read the copy instead of the cloud, so the file works without a connection.
 */
@Singleton
class OfflineFiles @Inject constructor(context: Context, private val cloudContentRepository: DispatchingCloudContentRepository) {

	private val copies = OfflineCopies.of(context)

	/** Cheap enough for list rows: a lookup in memory. */
	fun isOffline(file: CloudFileModel): Boolean {
		val cryptoFile = file.toCloudNode() as? CryptoFile ?: return false
		return copies.find(cryptoFile.cloudFile) != null
	}

	/** Downloads the ciphertext of [file] and keeps it. Call off the main thread. */
	@Throws(BackendException::class, IOException::class)
	fun keep(file: CloudFileModel, progressAware: ProgressAware<DownloadState>) {
		val cryptoFile = file.toCloudNode() as? CryptoFile ?: throw IllegalArgumentException("${file.name} is not in a vault")
		val vaultId = (cryptoFile.cloud as CryptoCloud).vault.id
		copies.store(vaultId, cryptoFile.cloudFile) { part ->
			FileOutputStream(part).use { out -> cloudContentRepository.read(cryptoFile.cloudFile, null, out, progressAware) }
		}
	}

	fun remove(file: CloudFileModel) {
		(file.toCloudNode() as? CryptoFile)?.let { copies.remove(it.cloudFile) }
	}

	fun deleteVault(vaultId: Long) {
		copies.deleteVault(vaultId)
	}
}
