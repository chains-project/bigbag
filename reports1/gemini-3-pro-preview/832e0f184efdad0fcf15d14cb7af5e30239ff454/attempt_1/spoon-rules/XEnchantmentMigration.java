package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class XEnchantmentMigration {

    /**
     * Processor to migrate from XEnchantment.parseEnchantment(...) to XEnchantment.matchXEnchantment(...)
     */
    public static class ParseToMatchProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check: The method being removed is "parseEnchantment"
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"parseEnchantment".equals(methodName)) {
                return false;
            }

            // 2. Owner Check: Ensure the method belongs to "XEnchantment"
            // We use string matching for robustness in NoClasspath mode.
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && 
                !owner.getQualifiedName().contains("XEnchantment") && 
                !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 3. Defensive checks
            // We assume if the method matches name and owner, it requires migration.
            // We do not check arguments strictly because the replacement (matchXEnchantment)
            // typically handles the same arguments (String or Enchantment) as the removed parser.
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Logic: Rename the method call.
            // Old: XEnchantment.parseEnchantment(...)
            // New: XEnchantment.matchXEnchantment(...)
            
            String oldName = invocation.getExecutable().getSimpleName();
            invocation.getExecutable().setSimpleName("matchXEnchantment");

            System.out.println("Refactored " + oldName + " to matchXEnchantment at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/832e0f184efdad0fcf15d14cb7af5e30239ff454/WorldwideChat/src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUIMainMenu.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/832e0f184efdad0fcf15d14cb7af5e30239ff454/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/832e0f184efdad0fcf15d14cb7af5e30239ff454/WorldwideChat/src/main/java/com/expl0itz/worldwidechat/inventory/wwctranslategui/WWCTranslateGUIMainMenu.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/832e0f184efdad0fcf15d14cb7af5e30239ff454/attempt_1/transformed");

        // CRITICAL CONFIGURATION: Preservation of comments and formatting
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Add the migration processor
        launcher.addProcessor(new ParseToMatchProcessor());

        try {
            System.out.println("Starting XEnchantment migration...");
            launcher.run();
            System.out.println("Migration completed. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}