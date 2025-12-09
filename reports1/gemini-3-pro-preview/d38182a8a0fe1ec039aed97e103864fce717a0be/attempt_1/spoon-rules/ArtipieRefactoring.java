package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtComment;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class ArtipieRefactoring {

    /**
     * Processor to analyze usages of AuthzSlice.
     * Based on Diff: AuthzSlice.response(...) was ADDED.
     * Strategy: Identify constructor calls to AuthzSlice. Since the constructor was not removed,
     * we do not force a replacement, but we flag them for review (e.g., adding a TODO).
     */
    public static class AuthzSliceProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Defensive Type Check (NoClasspath safety)
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null) {
                return false;
            }

            // 2. Check Class Name (Relaxed string matching for robustness)
            String qualifiedName = typeRef.getQualifiedName();
            if (!qualifiedName.contains("com.artipie.http.auth.AuthzSlice") &&
                !qualifiedName.endsWith("AuthzSlice")) {
                return false;
            }

            // 3. Skip if already analyzed/commented (prevent infinite loops or duplicate comments)
            for (CtComment comment : candidate.getComments()) {
                if (comment.getContent().contains("Check if AuthzSlice.response")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtConstructorCall<?> ctorCall) {
            // Since the API change is additive (NEW method), we strictly preserve behavior.
            // We inject a comment to suggest checking the new factory method.
            
            getFactory().Code().createComment(
                ctorCall, 
                "TODO: Check if AuthzSlice.response(...) should be used here (New API available)", 
                CtComment.CommentType.INLINE
            );

            System.out.println("Flagged AuthzSlice constructor at: " + 
                (ctorCall.getPosition().isValidPosition() ? ctorCall.getPosition().toString() : "Unknown Position"));
        }
    }

    public static void main(String[] args) {
        // 1. Configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/d38182a8a0fe1ec039aed97e103864fce717a0be/docker-adapter/src/test/java/com/artipie/docker/http/AuthScopeSliceTest.java"; // Default input
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d38182a8a0fe1ec039aed97e103864fce717a0be/attempt_1/transformed"; // Default output

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d38182a8a0fe1ec039aed97e103864fce717a0be/docker-adapter/src/test/java/com/artipie/docker/http/AuthScopeSliceTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d38182a8a0fe1ec039aed97e103864fce717a0be/attempt_1/transformed");

        // 2. CRITICAL: Sniper Printer Configuration for strict source preservation
        // Enable comments to ensure they are parsed and printed
        launcher.getEnvironment().setCommentEnabled(true);
        // Manually inject the SniperJavaPrettyPrinter
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Configuration for NoClasspath execution
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore missing types to prevent crashes
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        // 4. Register Processor
        launcher.addProcessor(new AuthzSliceProcessor());

        // 5. Run
        System.out.println("Starting Artipie Refactoring Scan...");
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}