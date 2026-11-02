package com.ceos24.autedi.domain.generation.service;

public class GenerationClientFailure extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public GenerationClientFailure(String code, String message) {
        this(code, message, true);
    }

    public GenerationClientFailure(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
