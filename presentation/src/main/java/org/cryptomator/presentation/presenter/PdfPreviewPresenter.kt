package org.cryptomator.presentation.presenter

import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.ui.activity.view.PdfPreviewView
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.PdfPages
import javax.inject.Inject
import timber.log.Timber

/** Shows a PDF from the decrypted copy inside the app and removes that copy when the viewer closes. */
@PerView
class PdfPreviewPresenter @Inject constructor( //
	exceptionMappings: ExceptionHandlers,  //
	private val fileUtil: FileUtil
) : Presenter<PdfPreviewView>(exceptionMappings) {

	fun openPages(file: CloudFileModel): PdfPages {
		return PdfPages(fileUtil.fileFor(file))
	}

	fun deleteDecryptedCopy(file: CloudFileModel) {
		val decrypted = fileUtil.fileFor(file)
		if (decrypted.exists() && !decrypted.delete()) {
			Timber.tag("PdfPreview").w("Failed to delete the decrypted copy of %s", file.name)
		}
	}
}
