package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Refactoring Template
 * 
 * note: The <dependency_change_diff> provided in the prompt was empty. 
 * This class serves as a robust template implementing all CRITICAL IMPLEMENTATION RULES 
 * (Sniper Printer, NoClasspath defenses, Type Checking) required for Spoon refactoring tasks.
 * 
 * Replace "TargetMethod", "TargetClass", and the transformation logic within the process() method 
 * to adapt to a specific API change.
 */
public class RefactoringTemplate {

    public static class TemplateProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check: Filter by the method name changing
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"targetMethodName".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check: Filter based on expected signature
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            // NEVER assume types are resolved. Use loose matching.
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> type = arg.getType();

            // Logic: 
            // - If type is NULL (unknown), we process it (assume it's the target).
            // - If type matches the NEW type, we skip it (already migrated).
            // - If type matches the OLD type (e.g., primitive), we process it.
            if (type != null) {
                String typeName = type.getQualifiedName();
                // If it's already the new type, skip
                if (typeName.contains("NewTargetType")) {
                    return false;
                }
                // If it's definitely not the old type and not unknown, skip
                if (!typeName.equals("int") && !typeName.equals("OldTargetType")) {
                    // return false; 
                    // Commented out to be permissive in NoClasspath mode
                }
            }

            // 4. Owner Check (Relaxed string matching)
            // Ensure the method belongs to the class we are refactoring
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null 
                && !owner.getQualifiedName().contains("TargetOwnerClass") 
                && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);

            // --- TRANSFORMATION LOGIC START ---
            
            // Example: Wrapping an argument (int -> Duration)
            // CtTypeReference<?> newTypeRef = factory.Type().createReference("java.time.Duration");
            // CtInvocation<?> replacement = factory.Code().createInvocation(
            //     factory.Code().createTypeAccess(newTypeRef),
            //     factory.Method().createReference(newTypeRef, factory.Type().voidPrimitiveType(), "ofMillis", factory.Type().integerPrimitiveType()),
            //     originalArg.clone()
            // );
            // originalArg.replace(replacement);

            // --- TRANSFORMATION LOGIC END ---

            System.out.println("Refactored " + invocation.getExecutable().getSimpleName() + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/f9763c18c7e1fa54fb67dcf3935aa5106807aba9/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f9763c18c7e1fa54fb67dcf3935aa5106807aba9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f9763c18c7e1fa54fb67dcf3935aa5106807aba9/code-coverage-api-plugin/ui-tests/src/main/java/io/jenkins/plugins/coverage/util/ChartUtil.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f9763c18c7e1fa54fb67dcf3935aa5106807aba9/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode for missing dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TemplateProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}