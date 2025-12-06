package com.example.core.prompt;

import java.util.Locale;
import java.util.Optional;

/**
 * Logical kinds of prompts that can be generated per file.
 * <p>
 * Each kind has:
 * <ul>
 *   <li>a stable {@link #id()} used in configuration and filenames</li>
 *   <li>a unique enum name used as suffix for environment overrides
 *       (PROMPT_IMPL_{ENUM_NAME})</li>
 * </ul>
 */
public enum PromptKind {

    DEFAULT("default"),
    IN_CONTEXT("in_context"),
    ANTHROPIC_SPOON_RULES("anthropic_spoon_rules"),
    FINAL_SPOON_RULES("final"),
    V2_IN_CONTEXT("v2_in_context"),
    BASELINE("baseline"),
    PROMPT_4("prompt_4"),
    PROMPT_5("prompt_5");

    private final String id;

    PromptKind(String id) {
        this.id = id;
    }

    /**
     * Stable identifier for this prompt kind, used in PROMPT_CLASSES and in
     * generated filenames.
     */
    public String id() {
        return id;
    }

    /**
     * Parse a configuration token into a {@link PromptKind}.
     * <p>
     * Accepts multiple synonyms, case-insensitive:
     * <ul>
     *   <li>"default"</li>
     *   <li>"in_context", "in-context", "context"</li>
         *   <li>"anthropic", "anthropic_spoon_rules"</li>
         *   <li>"final", "final_spoon_rules"</li>
     * </ul>
     */
    public static Optional<PromptKind> fromConfigToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "default" -> Optional.of(DEFAULT);
            case "in_context", "context" -> Optional.of(IN_CONTEXT);
            case "anthropic", "anthropic_spoon_rules" -> Optional.of(ANTHROPIC_SPOON_RULES);
            case "final", "final_spoon_rules" -> Optional.of(FINAL_SPOON_RULES);
            case "v2_in_context", "v2-in-context" -> Optional.of(V2_IN_CONTEXT);
            case "baseline", "base_line", "base-line" -> Optional.of(BASELINE);
            case "prompt_4", "prompt4", "prompt-4" -> Optional.of(PROMPT_4);
            case "prompt_5", "prompt5", "prompt-5" -> Optional.of(PROMPT_5);
           default -> Optional.empty();
        };
    }
}


