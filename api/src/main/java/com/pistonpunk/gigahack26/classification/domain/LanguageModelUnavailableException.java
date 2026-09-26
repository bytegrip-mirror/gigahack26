package com.pistonpunk.gigahack26.classification.domain;

public class LanguageModelUnavailableException extends RuntimeException {

    public LanguageModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public LanguageModelUnavailableException(String message) {
        super(message);
    }
}
