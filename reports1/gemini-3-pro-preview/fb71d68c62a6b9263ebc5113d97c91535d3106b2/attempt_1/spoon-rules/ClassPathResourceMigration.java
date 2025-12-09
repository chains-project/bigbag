package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ClassPathResourceMigration {

    /**
     * Processor to sanitize ClassPathResource constructor arguments.
     * It removes the leading '/' from String literals, as ClassLoader resources
     * should not have absolute paths.
     */
    public static class ClassPathResourceProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Check Constructor Type (Defensive for NoClasspath)
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().contains("ClassPathResource")) {
                return false;
            }

            // 2. Check Argument Count (Must have at least one argument: the path)
            if (candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Check First Argument Type
            CtExpression<?> firstArg = candidate.getArguments().get(0);
            
            // We only process String Literals. Variables are too risky in NoClasspath mode.
            if (!(firstArg instanceof CtLiteral)) {
                return false;
            }

            CtLiteral<?> literal = (CtLiteral<?>) firstArg;
            Object value = literal.getValue();

            // 4. Condition: Must be a String and start with "/"
            return value instanceof String && ((String) value).startsWith("/");
        }

        @Override
        public void process(CtConstructorCall<?> candidate) {
            CtLiteral<?> pathLiteral = (CtLiteral<?>) candidate.getArguments().get(0);
            String originalPath = (String) pathLiteral.getValue();
            
            // Refactoring: Remove the leading slash
            String sanitizedPath = originalPath.substring(1);

            // Create new literal with the sanitized value
            CtLiteral<String> newPath = getFactory().Code().createLiteral(sanitizedPath);

            // Replace the argument
            pathLiteral.replace(newPath);

            System.out.println("Sanitized ClassPathResource path at " + candidate.getPosition().toString() 
                + ": \"" + originalPath + "\" -> \"" + sanitizedPath + "\"");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/fb71d68c62a6b9263ebc5113d97c91535d3106b2/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fb71d68c62a6b9263ebc5113d97c91535d3106b2/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fb71d68c62a6b9263ebc5113d97c91535d3106b2/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fb71d68c62a6b9263ebc5113d97c91535d3106b2/attempt_1/transformed");

        // CRITICAL CONFIGURATION: Preservation of comments and formatting
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new ClassPathResourceProcessor());

        try {
            System.out.println("Starting ClassPathResource refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}