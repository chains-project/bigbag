package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Script.
 * 
 * GENERATED BASED ON EMPTY INPUT:
 * Since the <dependency_change_diff> provided was empty, this class generates a 
 * TEMPLATE structure adhering to the Critical Implementation Rules (Sniper, NoClasspath).
 * 
 * It implements a placeholder refactoring: Renaming "oldMethod" to "newMethod".
 */
public class BreakingChangeMigration {

    /**
     * Processor to handle breaking changes.
     * Logic: Renames method calls from 'oldMethod' to 'newMethod'.
     */
    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // We look for the method name strictly.
            CtExecutableReference<?> exec = candidate.getExecutable();
            if (!"oldMethod".equals(exec.getSimpleName())) {
                return false;
            }

            // 2. Owner/Context Check (Defensive for NoClasspath)
            // We use string matching rather than strict class resolution.
            CtTypeReference<?> declaringType = exec.getDeclaringType();
            if (declaringType != null) {
                String qualName = declaringType.getQualifiedName();
                // Ensure we are renaming the method on the correct class (or sub-classes loosely)
                // Using .contains to be safe against partial resolution in NoClasspath
                if (!qualName.contains("TargetClassName") && !qualName.equals("<unknown>")) {
                    return false;
                }
            }
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Rename the method
            CtExecutableReference<?> execRef = invocation.getExecutable();
            
            // In Spoon, renaming the reference usually updates the invocation in source code
            execRef.setSimpleName("newMethod");
            
            System.out.println("Refactored 'oldMethod' to 'newMethod' at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths - adjust as necessary
        String inputPath = "/home/kth/Documents/last_transformer/output/40feecdd9c649644668d7c84bb87b73a2b2723ca/CoursesPortlet/courses-portlet-api/src/main/java/org/jasig/portlet/courses/model/xml/CourseMeetingWrapper.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/40feecdd9c649644668d7c84bb87b73a2b2723ca/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/40feecdd9c649644668d7c84bb87b73a2b2723ca/CoursesPortlet/courses-portlet-api/src/main/java/org/jasig/portlet/courses/model/xml/CourseMeetingWrapper.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/40feecdd9c649644668d7c84bb87b73a2b2723ca/attempt_1/transformed");

        // =================================================================
        // CRITICAL IMPLEMENTATION RULES: PRESERVE SOURCE CODE & NOCLASSPATH
        // =================================================================
        
        // 1. Environment Configuration for NoClasspath Mode
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        // 2. Sniper Configuration (Strict Source Preservation)
        // Must enable comments first
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Manual injection of SniperJavaPrettyPrinter
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // =================================================================

        // Register the processor
        launcher.addProcessor(new MigrationProcessor());

        // Run the transformation
        try {
            System.out.println("Starting Refactoring in NoClasspath + Sniper mode...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}