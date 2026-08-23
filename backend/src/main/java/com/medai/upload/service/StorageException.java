package com.medai.upload.service;

/**
 * A storage backend could not complete an operation.
 *
 * <p>Both backends previously threw bare {@link RuntimeException}, which reaches the client as a
 * generic 500 carrying whatever the underlying library put in its message — for S3 that can
 * include the bucket name, the endpoint, and the credential identity in use.
 * {@code GlobalExceptionHandler} maps this to a fixed message and logs the cause server-side.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
