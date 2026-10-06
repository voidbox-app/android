package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.CloudFileModel;
import org.cryptomator.presentation.ui.activity.MediaPreviewActivity;

@Intent(MediaPreviewActivity.class)
public interface MediaPreviewIntent {

	CloudFileModel mediaFile();

	/** False or absent: the decrypted copy is expected on disk. */
	@Optional
	Boolean streamed();

}
