package com.vaultpass.exception;

public class EncryptionOperationException extends RuntimeException {
    public EncryptionOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
