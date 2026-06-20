package io.freshwater.banx.velocity.storage;

/** Unchecked wrapper for storage (SQL) failures so the public API stays exception-light. */
public class StorageException extends RuntimeException {
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
