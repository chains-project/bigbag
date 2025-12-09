package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ClassPathResourceRefactoring {

    /**
     * Processor to handle changes in org.springframework.core.io.ClassPathResource.
     * Strategy: Remove leading slashes from String literal arguments to ensure 
     * compatibility with stricter resource path handling.
     * 
     * Example:
     * - new ClassPathResource("/config/app.yaml") 
     * + new ClassPathResource("config/app.yaml")
     */
    public static class ClassPathResourceProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Type Check (Defensive for NoClasspath)
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null) {
                return false;
            }
            
            // Check for ClassPathResource (Qualified or Simple name to be robust)
            String qName = typeRef.getQualifiedName();
            if (!qName.contains("org.springframework.core.io.ClassPathResource") 
                && !qName.equals("ClassPathResource")) {
                return false;
            }

            // 2. Argument Validation
            // ClassPathResource constructors always take the path as the first argument.
            if (candidate.getArguments().isEmpty()) {
                return false;
            }

            CtExpression<?> firstArg = candidate.getArguments().get(0);

            // 3. Literal Check
            // We can only safely refactor string literals automatically. 
            // Variables (e.g., new ClassPathResource(myPath)) require runtime analysis.
            if (!(firstArg instanceof CtLiteral)) {
                return false;
            }

            Object value = ((CtLiteral<?>) firstArg).getValue();
            
            // 4. Refactoring Condition: Is it a String starting with '/'?
            return value instanceof String && ((String) value).startsWith("/");
        }

        @Override
        public void process(CtConstructorCall<?> candidate) {
            CtLiteral<?> literal = (CtLiteral<?>) candidate.getArguments().get(0);
            String originalValue = (String) literal.getValue();
            
            // Transformation: Remove the leading slash (index 1 to end)
            String newValue = originalValue.substring(1);

            // Create a new Literal with the sanitized path
            CtLiteral<String> newLiteral = getFactory().Code().createLiteral(newValue);
            
            // Replace the original argument
            literal.replace(newLiteral);
            
            System.out.println("Refactored ClassPathResource path at line " + candidate.getPosition().getLine() 
                + ": '" + originalValue + "' -> '" + newValue + "'");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/90cca141fc95d0510f92e23032c9d03813b074d8/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/90cca141fc95d0510f92e23032c9d03813b074d8/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/90cca141fc95d0510f92e23032c9d03813b074d8/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/90cca141fc95d0510f92e23032c9d03813b074d8/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Robust Refactoring
        
        // 1. Enable comments to prevent loss
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. NoClasspath mode (Assume user doesn't have all dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ClassPathResourceProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}