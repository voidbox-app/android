package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.presentation.model.CloudFileModel;
import org.cryptomator.presentation.ui.activity.PdfPreviewActivity;

@Intent(PdfPreviewActivity.class)
public interface PdfPreviewIntent {

	CloudFileModel pdfFile();

}
