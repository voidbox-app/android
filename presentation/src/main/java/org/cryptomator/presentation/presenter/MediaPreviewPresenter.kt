package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.ui.activity.view.MediaPreviewView
import org.cryptomator.presentation.util.FileUtil
import javax.inject.Inject
import timber.log.Timber

/**
 * Plays video and audio from the decrypted copy inside the app; the copy never leaves the
 * app's cache directory and is removed as soon as the player closes.
 */
@PerView
class MediaPreviewPresenter @Inject constructor( //
	exceptionMappings: ExceptionHandlers,  //
	private val fileUtil: FileUtil
) : Presenter<MediaPreviewView>(exceptionMappings) {

	fun mediaUri(file: CloudFileModel): Uri {
		return Uri.fromFile(fileUtil.fileFor(file))
	}

	fun deleteDecryptedCopy(file: CloudFileModel) {
		val decrypted = fileUtil.fileFor(file)
		if (decrypted.exists() && !decrypted.delete()) {
			Timber.tag("MediaPreview").w("Failed to delete the decrypted copy of %s", file.name)
		}
	}
}
