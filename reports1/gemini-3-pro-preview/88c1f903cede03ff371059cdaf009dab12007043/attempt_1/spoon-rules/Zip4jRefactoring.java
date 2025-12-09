package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class Zip4jRefactoring {

    /**
     * Processor to handle the relocation of ZipFile class.
     * <p>
     * Change:
     * - CLASS net.lingala.zip4j.core.ZipFile [REMOVED]
     * <p>
     * Strategy:
     * Identify references to the old 'net.lingala.zip4j.core.ZipFile'
     * and replace them with 'net.lingala.zip4j.ZipFile'.
     * This covers imports, variable declarations, and constructor calls.
     */
    public static class ZipFileMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive Check: Ensure candidate is not null
            if (candidate == null) return false;

            // Defensive Check: In NoClasspath, getQualifiedName() might return empty or behave unexpectedly
            // if the type is unknown, but here we explicitly look for a known pattern.
            String qName = candidate.getQualifiedName();
            
            // We match strictly on the old class name. 
            // Spoon resolves simple names (e.g., "ZipFile") to qualified names based on imports 
            // even in NoClasspath mode.
            return "net.lingala.zip4j.core.ZipFile".equals(qName);
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Create a reference to the new class location
            // New Package: net.lingala.zip4j
            // Class Name: ZipFile
            CtTypeReference<?> newTypeRef = getFactory().Type().createReference("net.lingala.zip4j.ZipFile");

            // Replace the AST node. 
            // This works for Imports, Variable Types, Object Instantiations, etc.
            candidate.replace(newTypeRef);

            if (candidate.getPosition().isValidPosition()) {
                System.out.println("Refactored ZipFile reference at line " + candidate.getPosition().getLine());
            } else {
                System.out.println("Refactored ZipFile reference (unknown position)");
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/88c1f903cede03ff371059cdaf009dab12007043/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/88c1f903cede03ff371059cdaf009dab12007043/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/88c1f903cede03ff371059cdaf009dab12007043/allure-maven/src/main/java/io/qameta/allure/maven/AllureCommandline.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/88c1f903cede03ff371059cdaf009dab12007043/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Code Preservation (Sniper Mode)
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Set NoClasspath to true to handle missing dependencies without crashing
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new ZipFileMigrationProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}