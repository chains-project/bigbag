package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class InterceptorBindingRefactoring {

    /**
     * Processor to migrate javax.interceptor.InterceptorBinding to jakarta.interceptor.InterceptorBinding.
     * This targets CtTypeReference to handle imports, annotations, and variable declarations.
     */
    public static class InterceptorBindingProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS = "javax.interceptor.InterceptorBinding";
        private static final String NEW_PACKAGE = "jakarta.interceptor";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding: handle nulls which can happen in NoClasspath mode
            if (candidate == null || candidate.getPackage() == null) {
                return false;
            }

            // 1. Qualified Name Check
            // We use string comparison to avoid resolving types in NoClasspath mode.
            // This handles both fully qualified usages and imported usages (if Spoon can resolve import context).
            return OLD_CLASS.equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // 2. Transformation
            // We retrieve or create the new Jakarta package
            CtPackage jakartaPackage = getFactory().Package().getOrCreate(NEW_PACKAGE);
            
            // We update the package of the type reference. 
            // Spoon's model will now point to jakarta.interceptor.InterceptorBinding.
            candidate.setPackage(jakartaPackage);

            System.out.println("Migrated InterceptorBinding at line " + candidate.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/3f30dfff617fd652412260ecf648a25769a27101/cdi-test/cdi-test-core/src/main/java/de/hilling/junit/cdi/scope/Replaceable.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3f30dfff617fd652412260ecf648a25769a27101/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/3f30dfff617fd652412260ecf648a25769a27101/cdi-test/cdi-test-core/src/main/java/de/hilling/junit/cdi/scope/Replaceable.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3f30dfff617fd652412260ecf648a25769a27101/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments to prevent loss during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting, indentation, and structure
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to allow running without full dependency JARs
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new InterceptorBindingProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}