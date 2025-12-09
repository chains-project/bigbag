package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ClosedInputStreamRefactoring {

    /**
     * Processor to handle the removal of ClosedInputStream.
     * Strategy:
     * 1. If we find 'new ClosedInputStream()', replace the whole instantiation with 'java.io.InputStream.nullInputStream()'.
     * 2. If we find a TypeReference to 'ClosedInputStream' (e.g., variable decl), replace it with 'java.io.InputStream'.
     */
    public static class ClosedInputStreamProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive check for NoClasspath environment
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }

            // Match by string name. We look for the removed class.
            // Using "endsWith" allows matching both fully qualified and simple names.
            return candidate.getQualifiedName().endsWith("ClosedInputStream") 
                || candidate.getQualifiedName().equals("org.apache.commons.io.input.ClosedInputStream");
        }

        @Override
        public void process(CtTypeReference<?> typeRef) {
            CtElement parent = typeRef.getParent();

            // Case 1: The type is being used in a constructor call: "new ClosedInputStream()"
            if (parent instanceof CtNewClass) {
                refactorInstantiation((CtNewClass<?>) parent);
            } 
            // Case 2: The type is used as a type declaration (Field, Variable, Parameter)
            // We ensure we aren't replacing the type inside the NewClass we just handled (though usually safe as node is replaced)
            else {
                refactorTypeReference(typeRef);
            }
        }

        /**
         * Converts 'new ClosedInputStream()' -> 'java.io.InputStream.nullInputStream()'
         */
        private void refactorInstantiation(CtNewClass<?> newClass) {
            Factory factory = getFactory();
            
            // Create reference to java.io.InputStream
            CtTypeReference<?> inputStreamRef = factory.Type().createReference("java.io.InputStream");
            
            // Create invocation: java.io.InputStream.nullInputStream()
            CtInvocation<?> nullInputStreamInvocation = factory.Code().createInvocation(
                factory.Code().createTypeAccess(inputStreamRef),
                factory.Method().createReference(
                    inputStreamRef,
                    inputStreamRef, // return type
                    "nullInputStream" // static method name (Java 11+)
                )
            );

            // Replace the 'new ...()' expression with the static method call
            newClass.replace(nullInputStreamInvocation);
            
            System.out.println("Refactored instantiation at line " + newClass.getPosition().getLine());
        }

        /**
         * Converts 'ClosedInputStream myVar' -> 'InputStream myVar'
         */
        private void refactorTypeReference(CtTypeReference<?> typeRef) {
            Factory factory = getFactory();
            CtTypeReference<?> inputStreamRef = factory.Type().createReference("java.io.InputStream");
            
            // We replace the reference itself. 
            // Spoon handles updating the parent (Variable, Field, etc.) automatically.
            typeRef.replace(inputStreamRef);
            
            System.out.println("Refactored Type Reference at line " + typeRef.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/ee0827d4c9bf80982241e8c3559dceb8b39063e4/plexus-archiver/src/main/java/org/codehaus/plexus/archiver/zip/ByteArrayOutputStream.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ee0827d4c9bf80982241e8c3559dceb8b39063e4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ee0827d4c9bf80982241e8c3559dceb8b39063e4/plexus-archiver/src/main/java/org/codehaus/plexus/archiver/zip/ByteArrayOutputStream.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ee0827d4c9bf80982241e8c3559dceb8b39063e4/attempt_1/transformed");

        // ---------------------------------------------------------
        // CRITICAL: Robust Sniper Configuration for Source Preservation
        // ---------------------------------------------------------
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Enable Auto-Imports to handle the new java.io.InputStream if needed
        launcher.getEnvironment().setAutoImports(true);
        // 3. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 4. NoClasspath mode (defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ClosedInputStreamProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}