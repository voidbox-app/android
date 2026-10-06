package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.RandomAccessContent
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.ui.activity.view.MediaPreviewView
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.VaultMedia
import javax.inject.Inject
import timber.log.Timber

/** Plays streamed media, or the decrypted copy, which is deleted when the player closes. */
@PerView
class MediaPreviewPresenter @Inject constructor( //
	exceptionMappings: ExceptionHandlers,  //
	private val fileUtil: FileUtil, //
	private val vaultMedia: VaultMedia
) : Presenter<MediaPreviewView>(exceptionMappings) {

	fun mediaUri(file: CloudFileModel): Uri {
		return Uri.fromFile(fileUtil.fileFor(file))
	}

	/** A placeholder; its extension tells Media3 the container. */
	fun streamUri(file: CloudFileModel): Uri {
		return Uri.parse("vault:///" + Uri.encode(file.name))
	}

	/** Call off the main thread. */
	@Throws(BackendException::class)
	fun openStream(file: CloudFileModel): RandomAccessContent {
		return vaultMedia.open(file)
	}

	fun deleteDecryptedCopy(file: CloudFileModel) {
		val decrypted = fileUtil.fileFor(file)
		if (decrypted.exists() && !decrypted.delete()) {
			Timber.tag("MediaPreview").w("Failed to delete the decrypted copy of %s", file.name)
		}
	}
}
