package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * GENERATED CODE - TEMPLATE
 * 
 * Note: The <dependency_change_diff> provided in the input was empty.
 * This class demonstrates the CRITICAL IMPLEMENTATION RULES (Sniper Printer, NoClasspath, Generics)
 * using a placeholder transformation:
 * 
 * Scenario: Renaming `com.legacy.Service.executeOld(String)` to `executeNew(String)`
 */
public class MigrationTool {

    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest filter)
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"executeOld".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner/Type Check (Defensive for NoClasspath)
            // We cannot rely on fully qualified names resolving perfectly in NoClasspath
            CtExecutableReference<?> execRef = candidate.getExecutable();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();

            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                // Check if it belongs to the target class (or unknown if libs are missing)
                if (!typeName.contains("Service") && !typeName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method
            CtExecutableReference<?> execRef = invocation.getExecutable();
            
            // Just renaming the reference updates the invocation in the AST
            execRef.setSimpleName("executeNew");
            
            System.out.println("Refactored method call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/endpoint/MessageController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/08e33c7bbc45ceac64564dadb9abbae0ed481ca5/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments to preserve them in output
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);

        // Register the processor
        launcher.addProcessor(new MethodRenameProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}