package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.HashMap;
import java.util.Map;

public class SpringFutureRefactoring {

    /**
     * Processor to migrate deprecated Spring Future types and TaskExecutors
     * to their modern Java/Spring equivalents.
     * 
     * Mappings:
     * - org.springframework.util.concurrent.ListenableFuture -> java.util.concurrent.CompletableFuture
     * - org.springframework.util.concurrent.SettableListenableFuture -> java.util.concurrent.CompletableFuture
     * - org.springframework.core.task.AsyncListenableTaskExecutor -> org.springframework.core.task.AsyncTaskExecutor
     */
    public static class SpringFutureProcessor extends AbstractProcessor<CtTypeReference<?>> {
        
        private static final Map<String, String> REPLACEMENTS = new HashMap<>();
        
        static {
            REPLACEMENTS.put("org.springframework.util.concurrent.ListenableFuture", "java.util.concurrent.CompletableFuture");
            REPLACEMENTS.put("org.springframework.util.concurrent.SettableListenableFuture", "java.util.concurrent.CompletableFuture");
            REPLACEMENTS.put("org.springframework.core.task.AsyncListenableTaskExecutor", "org.springframework.core.task.AsyncTaskExecutor");
        }

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive check for null
            if (candidate == null) return false;

            // In NoClasspath, we rely on string analysis of the qualified name.
            // We strip generics for the match (e.g. ListenableFuture<String> -> ListenableFuture)
            String qName = candidate.getQualifiedName();
            
            // Check if any key is contained in the qName (handling potential generic suffix issues in raw strings)
            // However, candidate.getQualifiedName() on a TypeReference usually returns the erased type name 
            // or the full name. We check against our map keys.
            for (String key : REPLACEMENTS.keySet()) {
                if (qName.equals(key)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            Factory factory = getFactory();
            String oldName = candidate.getQualifiedName();
            String newName = REPLACEMENTS.get(oldName);

            if (newName == null) return;

            // Create the new TypeReference
            CtTypeReference<?> newTypeRef = factory.Type().createReference(newName);

            // Preserve Generic Type Arguments (e.g., <String>)
            // If the original had ListenableFuture<String>, we want CompletableFuture<String>
            if (!candidate.getActualTypeArguments().isEmpty()) {
                newTypeRef.setActualTypeArguments(candidate.getActualTypeArguments());
            }

            // Perform the replacement
            candidate.replace(newTypeRef);
            
            System.out.println("Refactored: " + oldName + " -> " + newName + " at line " + candidate.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args if needed)
        String inputPath = "/home/kth/Documents/last_transformer/output/c4950c79dfe902dae8991ff722216c7ba787bf32/future-converter/common-test/src/main/java/net/javacrumbs/futureconverter/common/test/spring/SpringOriginalFutureTestHelper.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c4950c79dfe902dae8991ff722216c7ba787bf32/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/c4950c79dfe902dae8991ff722216c7ba787bf32/future-converter/common-test/src/main/java/net/javacrumbs/futureconverter/common/test/spring/SpringOriginalFutureTestHelper.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c4950c79dfe902dae8991ff722216c7ba787bf32/attempt_1/transformed");

        // CRITICAL: Configure Environment for Robust Sniper Mode
        // 1. Preserve Comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Use SniperJavaPrettyPrinter to preserve original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode (User might not have all deps)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new SpringFutureProcessor());

        try {
            System.out.println("Starting Spring Future Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}