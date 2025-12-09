package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.declaration.CtCompilationUnit;

import java.util.Collection;

public class ServletMigration {

    /**
     * Processor to migrate javax.servlet types to jakarta.servlet.
     * This handles:
     * 1. Class usages (e.g., Filter, ServletContext)
     * 2. Field access (e.g., DispatcherType.REQUEST)
     * 3. Method argument types (e.g., addFilter(..., Filter))
     * 4. Extends/Implements clauses
     */
    public static class JakartaServletProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive check for NoClasspath mode
            if (candidate == null) return false;
            
            // Check qualified name safely
            String qName = candidate.getQualifiedName();
            if (qName == null) return false;

            // Target the package javax.servlet and its subpackages (http, descriptor, etc.)
            // as identified in the diff (e.g., javax.servlet.DispatcherType, javax.servlet.http.Cookie)
            return qName.startsWith("javax.servlet.");
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            Factory factory = getFactory();
            String oldQName = candidate.getQualifiedName();
            
            // Calculate new qualified name
            String newQName = oldQName.replace("javax.servlet.", "jakarta.servlet.");

            // Create the new reference
            CtTypeReference<?> newRef = factory.Type().createReference(newQName);

            // Preserve generics (if any) to avoid stripping type arguments
            if (!candidate.getActualTypeArguments().isEmpty()) {
                newRef.setActualTypeArguments(candidate.getActualTypeArguments());
            }

            // Replace the old reference with the new one
            try {
                // replace() searches for the candidate in its parent and swaps it.
                // This updates variable types, return types, inheritance, etc.
                candidate.replace(newRef);
                System.out.println("Migrated: " + oldQName + " -> " + newQName + " at line " + candidate.getPosition().getLine());
            } catch (Exception e) {
                // Suppress replacement errors for detached nodes or special edge cases
                System.err.println("Skipped replacement for " + oldQName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Helper to process Imports explicitly, as standard processors might skip CompilationUnit metadata.
     */
    public static class ImportFixer extends CtScanner {
        private final JakartaServletProcessor processor;

        public ImportFixer(JakartaServletProcessor processor) {
            this.processor = processor;
        }

        @Override
        public void visitCtImport(CtImport ctImport) {
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) ctImport.getReference();
                if (processor.isToBeProcessed(ref)) {
                    processor.process(ref);
                }
            }
            super.visitCtImport(ctImport);
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/115827c6675f532a4d33751d1a0c0ba394bbd304/dropwizard-pac4j/src/main/java/org/pac4j/dropwizard/J2EHelper.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/115827c6675f532a4d33751d1a0c0ba394bbd304/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/115827c6675f532a4d33751d1a0c0ba394bbd304/dropwizard-pac4j/src/main/java/org/pac4j/dropwizard/J2EHelper.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/115827c6675f532a4d33751d1a0c0ba394bbd304/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (defensive assumption)
        launcher.getEnvironment().setNoClasspath(true);

        // Build the model
        launcher.buildModel();

        // Run the main processor on the code body
        JakartaServletProcessor processor = new JakartaServletProcessor();
        processor.setFactory(launcher.getFactory());
        launcher.getModel().processWith(processor);

        // Explicitly scan imports to ensure 'import javax.servlet.*' are updated
        // Standard model processing usually skips CompilationUnit imports
        ImportFixer importFixer = new ImportFixer(processor);
        Collection<CtCompilationUnit> units = launcher.getFactory().CompilationUnit().getMap().values();
        for (CtCompilationUnit cu : units) {
            importFixer.scan(cu);
        }

        // Save results
        launcher.prettyprint();
        System.out.println("Migration complete. Output in: " + outputPath);
    }
}