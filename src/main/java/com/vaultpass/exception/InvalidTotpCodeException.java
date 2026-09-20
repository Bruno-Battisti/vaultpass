package com.vaultpass.exception;

public class InvalidTotpCodeException extends RuntimeException {
    public InvalidTotpCodeException(String message) {
        super(message);
    }
}
