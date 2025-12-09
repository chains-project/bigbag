package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class PrettyPrinterConfigRefactoring {

    /**
     * Processor to handle the relocation of PrettyPrinterConfiguration.
     * It handles both Imports and inline FQN usages.
     */
    public static class PackageMigrationProcessor extends AbstractProcessor<CtCompilationUnit> {
        
        private static final String OLD_PKG = "com.github.javaparser.printer";
        private static final String NEW_PKG = "com.github.javaparser.printer.configuration";
        private static final String CLASS_NAME = "PrettyPrinterConfiguration";
        private static final String OLD_FQN = OLD_PKG + "." + CLASS_NAME;

        @Override
        public void process(CtCompilationUnit cu) {
            // 1. Process Imports explicitly (as scanners might miss them depending on configuration)
            for (CtImport imp : cu.getImports()) {
                if (imp.getReference() instanceof CtTypeReference) {
                    updateTypeReference((CtTypeReference<?>) imp.getReference());
                } else if (imp.getReference() instanceof CtExecutableReference) {
                    // Handle static imports of methods
                    updateTypeReference(((CtExecutableReference<?>) imp.getReference()).getDeclaringType());
                } else if (imp.getReference() instanceof CtFieldReference) {
                    // Handle static imports of fields
                    updateTypeReference(((CtFieldReference<?>) imp.getReference()).getDeclaringType());
                }
            }

            // 2. Process all usages in the code (Variables, Object creation, Inheritance, etc.)
            cu.accept(new CtScanner() {
                @Override
                public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
                    updateTypeReference(reference);
                    super.visitCtTypeReference(reference);
                }
            });
        }

        private void updateTypeReference(CtTypeReference<?> ref) {
            if (ref != null && OLD_FQN.equals(ref.getQualifiedName())) {
                CtPackageReference newPkg = getFactory().Package().getOrCreate(NEW_PKG);
                ref.setPackage(newPkg);
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/165381d26b2c3d2278fde88c16f95807506451fe/scheduler/safeplace/src/test/java/org/btrplace/safeplace/DSN.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/165381d26b2c3d2278fde88c16f95807506451fe/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/165381d26b2c3d2278fde88c16f95807506451fe/scheduler/safeplace/src/test/java/org/btrplace/safeplace/DSN.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/165381d26b2c3d2278fde88c16f95807506451fe/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments to prevent loss during printing
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to respect original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (defensive assumption)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new PackageMigrationProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}