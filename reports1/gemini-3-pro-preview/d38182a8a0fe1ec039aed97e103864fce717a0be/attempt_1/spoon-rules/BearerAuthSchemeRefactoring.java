package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class BearerAuthSchemeRefactoring {

    /**
     * Processor to migrate:
     * new BearerAuthScheme(auth, "realm") -> new BearerAuthScheme(auth)
     */
    public static class BearerAuthSchemeProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Check Class Name (Defensive string check for NoClasspath)
            CtTypeReference<?> type = candidate.getType();
            if (type == null || !type.getQualifiedName().contains("BearerAuthScheme")) {
                return false;
            }

            // 2. Check Argument Count
            // We are looking for the removed constructor which had 2 arguments.
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 2) {
                return false;
            }

            // 3. Check Argument Types (Defensive)
            // Arg 0: Should be TokenAuthentication (we skip strict check to be safe in NoClasspath, 
            // but we can check Arg 1 which is the distinct factor).
            
            // Arg 1: Should be String
            CtExpression<?> secondArg = args.get(1);
            CtTypeReference<?> secondArgType = secondArg.getType();

            // Logic: 
            // - If type is known (not null) AND it is NOT a String, it's not the target constructor.
            // - If type is null (unknown), we assume it might be our target and process it to be safe.
            if (secondArgType != null && !secondArgType.getQualifiedName().contains("String")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtConstructorCall<?> candidate) {
            // Strategy: Remove the second argument (the String realm/challenge).
            // This transforms `new BearerAuthScheme(auth, "string")` into `new BearerAuthScheme(auth)`.
            
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() == 2) {
                CtExpression<?> argToRemove = args.get(1);
                candidate.removeArgument(argToRemove);
                
                System.out.println("Refactored BearerAuthScheme constructor at line " 
                    + candidate.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/d38182a8a0fe1ec039aed97e103864fce717a0be/docker-adapter/src/test/java/com/artipie/docker/http/AuthTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d38182a8a0fe1ec039aed97e103864fce717a0be/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d38182a8a0fe1ec039aed97e103864fce717a0be/docker-adapter/src/test/java/com/artipie/docker/http/AuthTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d38182a8a0fe1ec039aed97e103864fce717a0be/attempt_1/transformed");

        // =========================================================
        // CRITICAL: Robust Sniper Configuration for Source Preservation
        // =========================================================
        
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter manually
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new BearerAuthSchemeProcessor());

        // Run transformation
        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}