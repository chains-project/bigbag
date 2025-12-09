package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule for org.springframework.util.FileSystemUtils.
 * 
 * ANALYSIS:
 * The provided dependency diff indicates:
 * - CLASS org.springframework.util.FileSystemUtils [status=UNCHANGED]
 * 
 * Since the class status is UNCHANGED, no AST transformations (replacements/removals) 
 * are strictly required by the diff. This processor is generated to identify and 
 * preserve usages of the class, acting as a boilerplate for potential future migrations
 * or usage auditing.
 */
public class FileSystemUtilsRefactoring {

    public static class FileSystemUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // Defensive coding: Ensure executable reference exists
            if (candidate.getExecutable() == null) {
                return false;
            }

            // Get declaring type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // Check if it belongs to FileSystemUtils
            // using relaxed string matching for NoClasspath compatibility
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                return qName.contains("org.springframework.util.FileSystemUtils") 
                    || qName.equals("FileSystemUtils");
            }
            
            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // The input diff explicitly states the class is UNCHANGED.
            // Therefore, no transformation logic is implemented.
            // This log serves to verify the processor matches the target correctly.
            System.out.println("Detected usage of FileSystemUtils (Unchanged) at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/1629113f03956a230738c47397c33f8ba2d11341/LPVS/src/main/java/com/lpvs/util/LPVSFileUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1629113f03956a230738c47397c33f8ba2d11341/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/1629113f03956a230738c47397c33f8ba2d11341/LPVS/src/main/java/com/lpvs/util/LPVSFileUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/1629113f03956a230738c47397c33f8ba2d11341/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually (Preserve layout/indentation)
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new FileSystemUtilsProcessor());
        
        try { 
            launcher.run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}