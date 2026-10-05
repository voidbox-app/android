package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.presentation.model.CloudFileModel;
import org.cryptomator.presentation.ui.activity.MediaPreviewActivity;

@Intent(MediaPreviewActivity.class)
public interface MediaPreviewIntent {

	CloudFileModel mediaFile();

}
