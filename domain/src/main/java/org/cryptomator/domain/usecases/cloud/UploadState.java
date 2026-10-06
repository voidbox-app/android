package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;

public class UploadState implements FileTransferState {

	private enum Kind {
		ENCRYPTION, UPLOAD, FINISHING
	}

	private final CloudFile file;
	private final Kind kind;

	private UploadState(CloudFile file, Kind kind) {
		this.kind = kind;
		this.file = file;
	}

	public static UploadState upload(CloudFile file) {
		return new UploadState(file, Kind.UPLOAD);
	}

	public static UploadState encryption(CloudFile file) {
		return new UploadState(file, Kind.ENCRYPTION);
	}

	/** Every byte has been handed to the server, which has not answered yet. */
	public static UploadState finishing(CloudFile file) {
		return new UploadState(file, Kind.FINISHING);
	}

	@Override
	public CloudFile file() {
		return file;
	}

	public boolean isUpload() {
		return kind == Kind.UPLOAD;
	}

	public boolean isEncryption() {
		return kind == Kind.ENCRYPTION;
	}

	public boolean isFinishing() {
		return kind == Kind.FINISHING;
	}

	public UploadState withFile(CloudFile file) {
		return new UploadState(file, kind);
	}
}
