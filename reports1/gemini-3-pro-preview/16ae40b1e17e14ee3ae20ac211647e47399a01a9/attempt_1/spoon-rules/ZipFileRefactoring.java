package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ZipFileRefactoring {

    /**
     * Processor to handle the relocation of ZipFile from
     * 'net.lingala.zip4j.core.ZipFile' to 'net.lingala.zip4j.ZipFile'.
     */
    public static class ZipFileProcessor extends AbstractProcessor<CtTypeReference<?>> {

        private static final String OLD_CLASS_FQN = "net.lingala.zip4j.core.ZipFile";
        private static final String NEW_PACKAGE = "net.lingala.zip4j";

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive coding: Check for nulls which might occur in NoClasspath
            if (candidate == null || candidate.getPackage() == null) {
                return false;
            }

            // check if the reference matches the old fully qualified name
            // We use string comparison to avoid classpath resolution issues
            return OLD_CLASS_FQN.equals(candidate.getQualifiedName());
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Retrieve or create the reference to the new package structure
            CtPackage newPackageRef = getFactory().Package().getOrCreate(NEW_PACKAGE);

            // Update the package of the type reference.
            // This modifies the AST node. Since imports are also references in Spoon,
            // this handles both explicit imports and fully qualified usages in code.
            candidate.setPackage(newPackageRef);

            System.out.println("Refactored ZipFile usage at line " + candidate.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/16ae40b1e17e14ee3ae20ac211647e47399a01a9/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/16ae40b1e17e14ee3ae20ac211647e47399a01a9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/16ae40b1e17e14ee3ae20ac211647e47399a01a9/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/16ae40b1e17e14ee3ae20ac211647e47399a01a9/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments preservation
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Add the migration processor
        launcher.addProcessor(new ZipFileProcessor());

        try {
            System.out.println("Starting Zip4j Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}