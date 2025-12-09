package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Rule generated based on detected API changes.
 * 
 * DETECTED DIFF (Assumed due to empty input):
 * - METHOD org.apache.commons.io.FileUtils.writeStringToFile(java.io.File, java.lang.String) [REMOVED]
 * + METHOD org.apache.commons.io.FileUtils.writeStringToFile(java.io.File, java.lang.String, java.nio.charset.Charset) [ADDED]
 * 
 * REFACTORING STRATEGY:
 * Appends 'java.nio.charset.StandardCharsets.UTF_8' as the third argument to legacy calls.
 */
public class FileUtilsRefactoring {

    public static class WriteStringToFileProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"writeStringToFile".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // Legacy method had 2 arguments (File, String). New one has 3.
            if (candidate.getArguments().size() != 2) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            // We verify the first argument is likely a File (or unknown/null in noclasspath)
            CtExpression<?> arg0 = candidate.getArguments().get(0);
            CtTypeReference<?> type0 = arg0.getType();
            if (type0 != null && !type0.getQualifiedName().contains("File") && !type0.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains("FileUtils") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // Transformation: Add StandardCharsets.UTF_8 as the 3rd argument
            
            // 1. Create Type References
            CtTypeReference<?> standardCharsetsRef = factory.Type().createReference("java.nio.charset.StandardCharsets");
            CtTypeReference<?> charsetRef = factory.Type().createReference("java.nio.charset.Charset");

            // 2. Build AST for "StandardCharsets.UTF_8"
            // We use AST building instead of snippets for robustness in Sniper mode
            CtTypeAccess<?> typeAccess = factory.Code().createTypeAccess(standardCharsetsRef);
            
            CtFieldReference<?> utf8Ref = factory.Field().createReference(
                standardCharsetsRef, 
                charsetRef, 
                "UTF_8"
            );
            
            CtFieldRead<?> utf8Read = factory.Code().createFieldRead();
            utf8Read.setTarget(typeAccess);
            utf8Read.setVariable(utf8Ref);

            // 3. Modify the invocation
            invocation.addArgument(utf8Read);

            System.out.println("Refactored writeStringToFile at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/7cda77e3b9981dba271f866ec727ad9c6af9c709/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/DapsValidator.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7cda77e3b9981dba271f866ec727ad9c6af9c709/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7cda77e3b9981dba271f866ec727ad9c6af9c709/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/daps/DapsValidator.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7cda77e3b9981dba271f866ec727ad9c6af9c709/attempt_1/transformed");

        // CRITICAL SETTINGS FOR SPOON
        
        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve code formatting exactly
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true to handle partial source code resolution
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new WriteStringToFileProcessor());
        
        try { 
            launcher.run(); 
            System.out.println("Refactoring execution finished.");
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}