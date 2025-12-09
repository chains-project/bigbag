package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Spoon Refactoring Recipe
 * 
 * Generated for Diff Scenario:
 * - METHOD org.apache.commons.io.FileUtils.writeStringToFile(java.io.File, java.lang.String) [REMOVED]
 * + METHOD org.apache.commons.io.FileUtils.writeStringToFile(java.io.File, java.lang.String, java.nio.charset.Charset) [ADDED]
 * 
 * Logic: Appends 'java.nio.charset.Charset.defaultCharset()' to existing 2-argument calls.
 */
public class FileUtilsRefactoring {

    public static class FileUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"writeStringToFile".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // We only care about the deprecated version with 2 arguments (File, String)
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Owner Check (Defensive/Loose string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("FileUtils")) {
                return false;
            }

            // 4. Type Check (Optional Defensive)
            // If the first argument is definitely NOT a File (and we know the type), skip.
            // In NoClasspath, getType() might be null, so we allow nulls.
            CtExpression<?> firstArg = candidate.getArguments().get(0);
            if (firstArg.getType() != null && !firstArg.getType().getQualifiedName().contains("File")) {
               // unlikely to be our target if type is known and wrong
               return false; 
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // Transformation: Add 3rd argument (Charset.defaultCharset())
            
            // 1. Create the expression for Charset.defaultCharset()
            // We use a snippet for simplicity, ensuring fully qualified name to avoid import issues
            CtExpression<?> charsetArg = factory.Code().createCodeSnippetExpression(
                "java.nio.charset.Charset.defaultCharset()"
            );

            // 2. Modify the invocation
            invocation.addArgument(charsetArg);

            System.out.println("Refactored FileUtils.writeStringToFile at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae17c8e47b93596cffeb2ec9241465cf39c7f8eb/attempt_1/transformed");

        // CRITICAL SETTINGS FOR PRESERVATION
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (robustness)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FileUtilsProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}