package com.pistonpunk.gigahack26.service;

public class LanguageModelUnavailableException extends RuntimeException {

    public LanguageModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public LanguageModelUnavailableException(String message) {
        super(message);
    }
}
