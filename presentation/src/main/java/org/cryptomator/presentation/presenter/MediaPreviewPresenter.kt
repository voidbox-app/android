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

/**
 * Plays video and audio inside the app: straight from the cloud when the file can be read in
 * pieces, otherwise from the decrypted copy inside the app's cache directory, which is removed as
 * soon as the player closes.
 */
@PerView
class MediaPreviewPresenter @Inject constructor( //
	exceptionMappings: ExceptionHandlers,  //
	private val fileUtil: FileUtil, //
	private val vaultMedia: VaultMedia
) : Presenter<MediaPreviewView>(exceptionMappings) {

	fun mediaUri(file: CloudFileModel): Uri {
		return Uri.fromFile(fileUtil.fileFor(file))
	}

	/** Only a name for the player; the bytes come from [openStream]. The extension tells Media3 which container to expect. */
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
