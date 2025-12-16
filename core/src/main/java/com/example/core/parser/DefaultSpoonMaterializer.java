package com.example.core.parser;

import com.example.core.prompt.SpoonRulesMaterializer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Default materializer for Spoon-based prompts that use the traditional format
 * with &lt;spoon_rules&gt; sections or markdown code blocks.
 * <p>
 * This materializer delegates to the existing {@link SpoonRulesMaterializer}
 * for backward compatibility with prompts like "final", "anthropic_spoon_rules",
 * "in_context", etc.
 */
public final class DefaultSpoonMaterializer implements Materializer {

    @Override
    public String id() {
        return "default_spoon";
    }

    @Override
    public Path materialize(Path promptOutputFile,
                            Path originalSourceFile,
                            Path commitReportDir,
                            String baseName) throws IOException {
        // Delegate to the existing SpoonRulesMaterializer
        return SpoonRulesMaterializer.materialize(promptOutputFile, originalSourceFile, commitReportDir, baseName);
    }
}

