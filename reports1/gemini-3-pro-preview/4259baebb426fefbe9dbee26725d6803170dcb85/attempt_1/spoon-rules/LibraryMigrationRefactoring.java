package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script
 * generated based on provided dependency diff (Hypothetical: Renaming/Migration).
 *
 * Scenarios handled:
 * 1. Robust NoClasspath handling (checking types via String matching).
 * 2. Strict source code preservation (SniperJavaPrettyPrinter).
 */
public class LibraryMigrationRefactoring {

    /**
     * Processor to handle method renaming or signature changes.
     * Note: Since the input diff was empty, this processor implements a 
     * generic placeholder migration:
     * 
     * - METHOD com.example.LegacyService.process(String) [REMOVED]
     * + METHOD com.example.LegacyService.execute(String) [ADDED]
     */
    public static class LegacyServiceProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> executable = candidate.getExecutable();

            // 1. Name Check (Old Method Name)
            if (!"process".equals(executable.getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner/Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            
            // In NoClasspath, declaringType might be null or <unknown>.
            // We check if it matches the target class name via string comparison.
            if (declaringType != null) {
                String qualName = declaringType.getQualifiedName();
                // Match target class "LegacyService"
                if (!qualName.contains("LegacyService") && !qualName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Argument Type Check (Optional Defensive Check)
            // Ensure the argument is likely a String (or unknown/variable in NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();
            if (argType != null && !argType.getQualifiedName().contains("String") && !argType.getQualifiedName().equals("<unknown>")) {
                // If we know for sure it's NOT a String (e.g., it's an int), skip.
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Modification: Rename the method from "process" to "execute"
            
            // We get the executable reference associated with this specific invocation
            CtExecutableReference<?> execRef = invocation.getExecutable();
            
            // Update the simple name
            execRef.setSimpleName("execute");

            System.out.println("Refactored 'process' to 'execute' at " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args or hardcoded)
        String inputPath = "/home/kth/Documents/last_transformer/output/4259baebb426fefbe9dbee26725d6803170dcb85/lti-launch/src/main/java/edu/ksu/lti/launch/spring/config/LtiLaunchSecurityConfig.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4259baebb426fefbe9dbee26725d6803170dcb85/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/4259baebb426fefbe9dbee26725d6803170dcb85/lti-launch/src/main/java/edu/ksu/lti/launch/spring/config/LtiLaunchSecurityConfig.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/4259baebb426fefbe9dbee26725d6803170dcb85/attempt_1/transformed");

        // =========================================================
        // CRITICAL CONFIGURATION: NO-CLASSPATH & SNIPER MODE
        // =========================================================
        
        // 1. Configure Environment for NoClasspath (Symbols may be unknown)
        launcher.getEnvironment().setNoClasspath(true);

        // 2. Enable Comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);

        // 3. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // =========================================================

        launcher.addProcessor(new LegacyServiceProcessor());

        try {
            System.out.println("Starting Spoon Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}