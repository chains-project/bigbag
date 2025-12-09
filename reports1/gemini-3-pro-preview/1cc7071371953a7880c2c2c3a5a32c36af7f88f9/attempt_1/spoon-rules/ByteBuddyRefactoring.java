package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtImportKind;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Processor for ByteBuddy Migration.
 * 
 * Analyzes the removal of `org.assertj.core.internal.bytebuddy` and migrates usages
 * to the official `net.bytebuddy` package.
 */
public class ByteBuddyRefactoring {

    public static class ByteBuddyMigrationProcessor extends AbstractProcessor<CtElement> {
        private static final String OLD_PREFIX = "org.assertj.core.internal.bytebuddy";
        private static final String NEW_PREFIX = "net.bytebuddy";

        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            // We process Imports to handle wildcard imports (import pkg.*)
            if (candidate instanceof CtImport) {
                return true;
            }
            // We process TypeReferences to handle FQNs, variable types, and single imports
            if (candidate instanceof CtTypeReference) {
                return true;
            }
            return false;
        }

        @Override
        public void process(CtElement element) {
            if (element instanceof CtImport) {
                processImport((CtImport) element);
            } else if (element instanceof CtTypeReference) {
                processTypeReference((CtTypeReference<?>) element);
            }
        }

        /**
         * Handles package updates for on-demand imports (e.g., import old.pkg.*;)
         */
        private void processImport(CtImport ctImport) {
            // Only handle wildcard imports here. Single type imports contain a CtTypeReference
            // which is visited separately by the scanner and handled in processTypeReference.
            if (ctImport.getImportKind() == CtImportKind.ALL_TYPES) {
                if (ctImport.getReference() instanceof CtPackageReference) {
                    CtPackageReference pkgRef = (CtPackageReference) ctImport.getReference();
                    String oldName = pkgRef.getQualifiedName();
                    
                    if (oldName != null && oldName.startsWith(OLD_PREFIX)) {
                        String newName = oldName.replace(OLD_PREFIX, NEW_PREFIX);
                        
                        // Create a new reference for the new package
                        CtPackageReference newPkgRef = getFactory().Package().createReference(newName);
                        ctImport.setReference(newPkgRef);
                        
                        System.out.println("[Import] Refactored wildcard import: " + oldName + " -> " + newName);
                    }
                }
            }
        }

        /**
         * Handles type updates in code (variable declarations, extends, etc.) and single imports.
         */
        private void processTypeReference(CtTypeReference<?> ref) {
            // Defensive checks for NoClasspath mode
            if (ref == null || ref.isPrimitive()) {
                return;
            }

            // 1. Resolve the Top-Level Type
            // In case of Inner Classes (Outer.Inner), the package is defined on the Outer class.
            // We climb up the declaring types to ensure we modify the root container.
            CtTypeReference<?> topLevel = ref;
            while (topLevel.getDeclaringType() != null) {
                topLevel = topLevel.getDeclaringType();
            }

            // 2. Inspect the Package
            CtPackageReference pkg = topLevel.getPackage();
            // If package is null (unresolved/default), we cannot process it safely.
            if (pkg == null) {
                return;
            }

            String pkgName = pkg.getQualifiedName();
            // Check if it matches the removed AssertJ internal package
            if (pkgName != null && pkgName.startsWith(OLD_PREFIX)) {
                
                // 3. Construct the new package name
                String newPkgName = pkgName.replace(OLD_PREFIX, NEW_PREFIX);
                
                // 4. Update the package reference
                // This mutates the AST node. Since Spoon reuses references, this might fix 
                // multiple occurrences at once, but the check is idempotent.
                topLevel.setPackage(getFactory().Package().createReference(newPkgName));
                
                System.out.println("[Type] Refactored: " + topLevel.getSimpleName() + 
                                   " (" + pkgName + " -> " + newPkgName + ")");
            }
        }
    }

    public static void main(String[] args) {
        // Default configuration (can be overridden by args in real usage)
        String inputPath = "/home/kth/Documents/last_transformer/output/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/assertj-vavr/src/main/java/org/assertj/vavr/api/VavrAssumptions.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/assertj-vavr/src/main/java/org/assertj/vavr/api/VavrAssumptions.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1cc7071371953a7880c2c2c3a5a32c36af7f88f9/attempt_1/transformed");

        // --- CRITICAL CONFIGURATION FOR SOURCE PRESERVATION ---
        // 1. Enable comments to preserve Javadoc/inline comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Use SniperJavaPrettyPrinter to print only modified parts and preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode (robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);
        // ------------------------------------------------------

        launcher.addProcessor(new ByteBuddyMigrationProcessor());

        try {
            System.out.println("Starting Refactoring: org.assertj.core.internal.bytebuddy -> net.bytebuddy");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}