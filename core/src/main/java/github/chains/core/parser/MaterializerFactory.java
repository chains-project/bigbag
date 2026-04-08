package github.chains.core.parser;

/**
 * Factory for obtaining the appropriate materializer for a given prompt type.
 * <p>
 * This factory maps prompt identifiers to their corresponding materializers.
 * To add support for a new prompt type with a different output format, create
 * a new Materializer implementation and add a case in {@link #getMaterializer(String)}.
 */
public final class MaterializerFactory {

    private MaterializerFactory() {
        // utility class
    }

    /**
     * Returns the materializer for the given prompt identifier.
     * 
     * @param promptId the prompt identifier (e.g., "baseline_spoon", "final", etc.)
     * @return the corresponding materializer, or the default Spoon materializer if no specific one is registered
     */
    public static Materializer getMaterializer(String promptId) {
        if (promptId == null || promptId.isBlank()) {
            return new DefaultSpoonMaterializer();
        }

        String normalized = promptId.trim().toLowerCase();

        return switch (normalized) {
            // Specific materializers for prompts with unique output formats
            case "baseline_spoon", "baseline-spoon" -> new BaselineSpoonMaterializer();
            
            // OpenRewrite materializer for prompts that generate OpenRewrite recipes
            case "openrewrite", "open-rewrite", "rewrite" -> new OpenRewriteMaterializer();
            
            // Default materializer for all other Spoon-based prompts
            // These prompts use the traditional format with <spoon_rules> sections
            // or markdown code blocks that need standard processing
            case "default" -> new DefaultSpoonMaterializer();
            case "in_context", "in-context", "context" -> new DefaultSpoonMaterializer();
            case "anthropic_spoon_rules", "anthropic", "anthropic_spoon" -> new DefaultSpoonMaterializer();
            case "final", "final_spoon_rules" -> new DefaultSpoonMaterializer();
            case "v2_in_context", "v2-in-context" -> new DefaultSpoonMaterializer();
            case "baseline", "base_line", "base-line" -> new DefaultSpoonMaterializer();
            case "prompt_4", "prompt4", "prompt-4" -> new DefaultSpoonMaterializer();
            case "prompt_5", "prompt5", "prompt-5" -> new DefaultSpoonMaterializer();
            
            // Fallback: return default materializer for unknown prompt types
            default -> new DefaultSpoonMaterializer();
        };
    }

    /**
     * Checks if a materializer is available for the given prompt identifier.
     * 
     * @param promptId the prompt identifier
     * @return true if a materializer exists for this prompt type, false otherwise
     */
    public static boolean hasMaterializer(String promptId) {
        return getMaterializer(promptId) != null;
    }
}

