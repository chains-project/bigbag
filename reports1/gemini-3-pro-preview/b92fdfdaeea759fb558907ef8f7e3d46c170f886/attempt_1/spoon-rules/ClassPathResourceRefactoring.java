package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ClassPathResourceRefactoring {

    /**
     * Processor to sanitize ClassPathResource paths.
     * Strategy: Detects `new ClassPathResource("/path")` and transforms it to `new ClassPathResource("path")`.
     * This fixes source incompatibility issues regarding stricter path validation in newer Spring versions.
     */
    public static class ClassPathResourcePathProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Type Check (Defensive for NoClasspath)
            // We check the type being instantiated (new X()).
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().contains("ClassPathResource")) {
                return false;
            }

            // 2. Argument Validation
            // ClassPathResource constructors always take the path as the first argument.
            if (candidate.getArguments().isEmpty()) {
                return false;
            }

            CtExpression<?> firstArg = candidate.getArguments().get(0);

            // 3. Literal Check
            // We only safely refactor String literals. Dynamic expressions (variables) are harder to prove safe statically.
            if (!(firstArg instanceof CtLiteral)) {
                return false;
            }

            CtLiteral<?> literal = (CtLiteral<?>) firstArg;
            Object value = literal.getValue();

            // 4. Pattern Match: Check if it is a String starting with "/"
            if (value instanceof String) {
                String path = (String) value;
                return path.startsWith("/");
            }

            return false;
        }

        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = constructorCall.getArguments().get(0);
            
            // Defensively cast safely checked in isToBeProcessed
            CtLiteral<?> originalLiteral = (CtLiteral<?>) originalArg;
            String oldPath = (String) originalLiteral.getValue();
            
            // Transformation: Remove leading slash
            String newPath = oldPath.substring(1); // strips the first '/'

            // Create new String literal
            CtLiteral<String> replacement = factory.Code().createLiteral(newPath);

            // Preserve comments/layout if possible by using the original as a template basis (optional but good practice)
            replacement.setComments(originalArg.getComments());

            // Apply replacement
            originalArg.replace(replacement);

            System.out.println("Refactored ClassPathResource path at line " + constructorCall.getPosition().getLine() 
                + ": \"" + oldPath + "\" -> \"" + newPath + "\"");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/b92fdfdaeea759fb558907ef8f7e3d46c170f886/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b92fdfdaeea759fb558907ef8f7e3d46c170f886/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/b92fdfdaeea759fb558907ef8f7e3d46c170f886/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b92fdfdaeea759fb558907ef8f7e3d46c170f886/attempt_1/transformed");

        // ========================================================================
        // CRITICAL IMPLEMENTATION RULES: PRESERVE FORMATTING
        // ========================================================================
        
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for high-fidelity source code preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive Coding: Enable NoClasspath mode to handle missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new ClassPathResourcePathProcessor());

        try {
            System.out.println("Starting ClassPathResource refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}