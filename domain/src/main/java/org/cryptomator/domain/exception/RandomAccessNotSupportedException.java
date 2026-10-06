package org.cryptomator.domain.exception;

/**
 * The cloud cannot read part of a file: the backend has no ranged reads, or the server ignored
 * the requested range. Callers fall back to downloading the whole file.
 */
public class RandomAccessNotSupportedException extends BackendException {

	public RandomAccessNotSupportedException() {
		super();
	}

	public RandomAccessNotSupportedException(String message) {
		super(message);
	}

}
