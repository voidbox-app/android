package org.cryptomator.presentation.util

import android.content.Context
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.repository.DispatchingCloudContentRepository
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.RandomAccessContent
import org.cryptomator.domain.usecases.DownloadFileReplacingProgressAware
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.presentation.model.CloudFileModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Text of a vault file decrypted in memory: from the offline copy when there is one, otherwise from a temporary copy of the ciphertext. */
@Singleton
class VaultTextFiles @Inject constructor(context: Context, private val cloudContentRepository: DispatchingCloudContentRepository, private val offlineFiles: OfflineFiles) {

	private val cacheDir: File = context.cacheDir

	/** Call off the main thread; interrupting the thread stops the download. Closing the result deletes the temporary ciphertext. */
	@Throws(BackendException::class, IOException::class)
	fun open(file: CloudFileModel, progressAware: ProgressAware<DownloadState>): RandomAccessContent {
		val cryptoFile = file.toCloudNode() as? CryptoFile ?: throw IllegalArgumentException("${file.name} is not in a vault")
		if (offlineFiles.isOffline(file)) {
			return cloudContentRepository.openRandomAccess(cryptoFile)
		}
		val ciphertext = File.createTempFile(UUID.randomUUID().toString(), CIPHERTEXT_SUFFIX, cacheDir)
		try {
			val progress = DownloadFileReplacingProgressAware(cryptoFile, CancellableProgress(progressAware))
			FileOutputStream(ciphertext).use { out -> cloudContentRepository.read(cryptoFile.cloudFile, null, out, progress) }
			return TemporaryCiphertextContent(cloudContentRepository.openRandomAccess(cryptoFile, ciphertext), ciphertext)
		} catch (e: Exception) {
			ciphertext.delete()
			throw e
		}
	}

	/** Whether a text of [size] bytes can be laid out in the editor without running out of memory on this device. */
	fun fitsInEditor(size: Long): Boolean {
		val runtime = Runtime.getRuntime()
		val available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
		return size * EDITOR_BYTES_PER_TEXT_BYTE <= (available * EDITOR_SHARE_OF_AVAILABLE_MEMORY).toLong()
	}

	private class CancellableProgress(private val delegate: ProgressAware<DownloadState>) : ProgressAware<DownloadState> {

		override fun onProgress(progress: Progress<DownloadState>) {
			if (Thread.currentThread().isInterrupted) {
				throw InterruptedIOException("Loading was cancelled")
			}
			delegate.onProgress(progress)
		}
	}

	private class TemporaryCiphertextContent(private val content: RandomAccessContent, private val ciphertext: File) : RandomAccessContent by content {

		override fun close() {
			content.close()
			ciphertext.delete()
		}
	}

	companion object {

		private const val CIPHERTEXT_SUFFIX = ".crypto"
		private const val EDITOR_BYTES_PER_TEXT_BYTE = 24L
		private const val EDITOR_SHARE_OF_AVAILABLE_MEMORY = 0.6
	}
}
