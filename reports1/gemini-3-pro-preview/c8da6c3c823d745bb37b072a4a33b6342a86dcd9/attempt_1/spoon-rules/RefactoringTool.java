package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Spoon Refactoring Tool
 * Generated for: Hypothetical Diff (Method Rename)
 * Pattern: com.example.LegacyService.performAction() -> executeAction()
 */
public class RefactoringTool {

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // Target definitions
        private static final String TARGET_CLASS_TOKEN = "LegacyService";
        private static final String OLD_METHOD_NAME = "performAction";
        private static final String NEW_METHOD_NAME = "executeAction";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Safe Executable Check
            CtExecutableReference<?> exec = candidate.getExecutable();
            if (exec == null) return false;

            // 2. Name Check
            if (!OLD_METHOD_NAME.equals(exec.getSimpleName())) {
                return false;
            }

            // 3. Argument Count Check (Example: expecting 0 args)
            // Adjust this based on specific method signature changes
            if (candidate.getArguments().size() != 0) {
                return false;
            }

            // 4. Owner/Type Check (Defensive for NoClasspath)
            // We use string matching because full type resolution fails in NoClasspath
            CtTypeReference<?> declaringType = exec.getDeclaringType();
            if (declaringType != null) {
                String qualifiedName = declaringType.getQualifiedName();
                // Match exact class or ensure it's not a known unrelated type
                if (!qualifiedName.contains(TARGET_CLASS_TOKEN) && !qualifiedName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // 5. Apply Refactoring
            // In a rename scenario, we update the simple name of the executable reference.
            CtExecutableReference<?> execRef = invocation.getExecutable();
            
            // Log the change
            System.out.println("Refactoring method at " + invocation.getPosition().toString());
            
            // Perform renaming
            execRef.setSimpleName(NEW_METHOD_NAME);
            
            // Note: If arguments needed wrapping (e.g., int -> Duration), 
            // logic would go here using getFactory().Code().createInvocation(...)
        }
    }

    public static void main(String[] args) {
        // Configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/protocol/MessageService.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/c8da6c3c823d745bb37b072a4a33b6342a86dcd9/attempt_1/transformed");

        // =========================================================
        // CRITICAL: Sniper Configuration for Source Fidelity
        // =========================================================
        
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter
        // This ensures unrelated code (indentation, whitespace) remains untouched.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. NoClasspath Mode
        // Allows running without compiling the project or having dependencies.
        launcher.getEnvironment().setNoClasspath(true);

        // Add Processor
        launcher.addProcessor(new MethodRenameProcessor());

        // Run
        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: ");
            e.printStackTrace();
        }
    }
}