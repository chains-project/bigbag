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
     * Processor to sanitize ClassPathResource paths.
     * Spring 6+ / recent updates may require paths passed to ClassLoader (via ClassPathResource)
     * to NOT have a leading slash.
     * 
     * Pattern: new ClassPathResource("/path/to/file") -> new ClassPathResource("path/to/file")
     */
    public static class ClassPathResourcePathProcessor extends AbstractProcessor<CtConstructorCall<?>> {

        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            // 1. Type Check: Must be ClassPathResource
            CtTypeReference<?> typeRef = candidate.getType();
            if (typeRef == null || !typeRef.getQualifiedName().endsWith("ClassPathResource")) {
                return false;
            }

            // 2. Argument Count Check
            // We focus on constructor(String) or constructor(String, ClassLoader).
            // We avoid constructor(String, Class) because Class.getResource handles '/' correctly.
            int argCount = candidate.getArguments().size();
            if (argCount != 1 && argCount != 2) {
                return false;
            }

            // 3. First Argument Check: Must be a String Literal starting with "/"
            CtExpression<?> pathArg = candidate.getArguments().get(0);
            if (!(pathArg instanceof CtLiteral)) {
                return false;
            }
            Object value = ((CtLiteral<?>) pathArg).getValue();
            if (!(value instanceof String) || !((String) value).startsWith("/")) {
                return false;
            }

            // 4. Second Argument Check (Defensive)
            if (argCount == 2) {
                CtExpression<?> secondArg = candidate.getArguments().get(1);
                CtTypeReference<?> secondArgType = secondArg.getType();
                
                // If it is explicitly a Class (e.g. MyClass.class), we SKIP because "/abs" is valid there.
                // In NoClasspath, we might not know, but if it looks like "Class", we skip.
                if (secondArgType != null && secondArgType.getQualifiedName().contains("Class")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtConstructorCall<?> candidate) {
            CtLiteral<String> pathArg = (CtLiteral<String>) candidate.getArguments().get(0);
            String originalPath = pathArg.getValue();
            
            // Refactoring: Remove the leading slash
            String sanitizedPath = originalPath.substring(1);
            
            pathArg.setValue(sanitizedPath);
            
            System.out.println("Refactored ClassPathResource path at line " + candidate.getPosition().getLine() 
                + ": \"" + originalPath + "\" -> \"" + sanitizedPath + "\"");
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/315060cd2c7a3812a4adf614b042de84e9c39da4/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/315060cd2c7a3812a4adf614b042de84e9c39da4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/315060cd2c7a3812a4adf614b042de84e9c39da4/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/315060cd2c7a3812a4adf614b042de84e9c39da4/attempt_1/transformed");

        // CRITICAL SETTINGS for Robustness
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Allow running without full classpath dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ClassPathResourcePathProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}