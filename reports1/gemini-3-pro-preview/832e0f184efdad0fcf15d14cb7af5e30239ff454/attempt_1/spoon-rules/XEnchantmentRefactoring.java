package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class XEnchantmentRefactoring {

    /**
     * Processor to migrate XSeries XEnchantment.parseEnchantment() to matchXEnchantment().
     * 
     * Analysis:
     * - parseEnchantment() was removed.
     * - matchXEnchantment() exists and serves the same purpose (resolution of Enchants).
     * - Refactoring: Rename method call.
     */
    public static class ParseEnchantmentProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // We are looking for "parseEnchantment"
            if (!"parseEnchantment".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If the declaring type is unknown (null), we skip to be safe, 
            // though in some loose scripts you might process just by name.
            if (declaringType == null) {
                return false;
            }

            String qName = declaringType.getQualifiedName();
            
            // Check if it belongs to XEnchantment. 
            // qName might be "com.cryptomorin.xseries.XEnchantment" or just "XEnchantment" in NoClasspath.
            if (!qName.contains("XEnchantment")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method from parseEnchantment to matchXEnchantment
            // We modify the ExecutableReference directly.
            invocation.getExecutable().setSimpleName("matchXEnchantment");
            
            System.out.println("Refactored XEnchantment.parseEnchantment -> matchXEnchantment at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/832e0f184efdad0fcf15d14cb7af5e30239ff454/WorldwideChat/src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUITargetLanguage.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/832e0f184efdad0fcf15d14cb7af5e30239ff454/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/832e0f184efdad0fcf15d14cb7af5e30239ff454/WorldwideChat/src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUITargetLanguage.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/832e0f184efdad0fcf15d14cb7af5e30239ff454/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Sniper Configuration
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting/indentation of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive assumption)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new ParseEnchantmentProcessor());

        // Run transformation
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}