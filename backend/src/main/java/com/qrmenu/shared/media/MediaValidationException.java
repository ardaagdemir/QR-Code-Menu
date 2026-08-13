package com.qrmenu.shared.media;

/**
 * Extends IllegalArgumentException so ApiExceptionHandler's existing
 * {@code IllegalArgumentException -> 400} mapping covers it without a new case.
 */
public class MediaValidationException extends IllegalArgumentException {

    public MediaValidationException(String message) {
        super(message);
    }
}
