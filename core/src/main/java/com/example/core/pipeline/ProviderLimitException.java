package com.example.core.pipeline;

import se.kth.models.Attempt;

import java.util.List;

/**
 * Thrown when a provider hard-limit is hit (e.g. OpenRouter no credits, OpenCode free-tier).
 * Carries the failure attempt so the caller can write it to the JSON report before stopping.
 */
public class ProviderLimitException extends RuntimeException {

    private final List<Attempt> attempts;

    public ProviderLimitException(String message, List<Attempt> attempts) {
        super(message);
        this.attempts = attempts;
    }

    public List<Attempt> getAttempts() {
        return attempts;
    }
}
