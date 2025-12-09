package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Strategy:
 * The diff indicates that `org.springframework.core.io.ClassPathResource` has been modified
 * with `sourceCompatible=false` (likely due to changes in the `Resource` interface hierarchy).
 * 
 * To ensure forward compatibility and standardize usage in the new version, this rule 
 * normalizes `ClassPathResource` instantiation. Specifically, it removes leading slashes 
 * from String literal paths (e.g., `new ClassPathResource("/file.txt")` -> `new ClassPathResource("file.txt")`).
 * This aligns with Spring's resource loading best practices where ClassPath resources are relative.
 */
public class ClassPathResourceRefactoring {

    public static class ClassPathResourceProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Target Type Check (Defensive for NoClasspath)
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().contains("ClassPathResource")) {
                return false;
            }

            // 2. Argument Check: We are looking for new ClassPathResource(String)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();

            // 3. Defensive Type Check on Argument
            // In NoClasspath, type might be null. If known, ensure it's String.
            if (argType != null && !argType.getQualifiedName().contains("String")) {
                return false;
            }

            // 4. Literal Check: We can only safely refactor String literals
            if (!(arg instanceof CtLiteral)) {
                return false;
            }

            // 5. Check if refactoring is needed (starts with "/")
            Object value = ((CtLiteral<?>) arg).getValue();
            return value instanceof String && ((String) value).startsWith("/");
        }

        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            CtExpression<?> arg = constructorCall.getArguments().get(0);
            
            // Cast is safe due to isToBeProcessed checks
            CtLiteral<String> literal = (CtLiteral<String>) arg;
            String originalPath = literal.getValue();

            // Transformation: Remove the leading slash
            String newPath = originalPath.substring(1); // Strips the first char '/'

            // Create new literal to replace the old one
            CtLiteral<String> newLiteral = getFactory().Code().createLiteral(newPath);
            
            // Replace the argument
            literal.replace(newLiteral);
            
            System.out.println("Refactored ClassPathResource path at line " + 
                constructorCall.getPosition().getLine() + ": " + originalPath + " -> " + newPath);
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/79759978f25c94d97f340c80ef0e77c3ee6f8cfc/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/79759978f25c94d97f340c80ef0e77c3ee6f8cfc/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/79759978f25c94d97f340c80ef0e77c3ee6f8cfc/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/79759978f25c94d97f340c80ef0e77c3ee6f8cfc/attempt_1/transformed");

        // CRITICAL SETTINGS FOR PRESERVING FORMATTING
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve original code structure
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ClassPathResourceProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}