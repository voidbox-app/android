package org.cryptomator.domain.exception;

/** The cloud cannot read part of a file; callers download it whole instead. */
public class RandomAccessNotSupportedException extends BackendException {

	public RandomAccessNotSupportedException() {
		super();
	}

	public RandomAccessNotSupportedException(String message) {
		super(message);
	}

}
