package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Transformation Template
 * Generated for: Missing Dependency Diff (Template Mode)
 * 
 * This class implements a robust refactoring strategy using Spoon's Sniper mode
 * to preserve formatting/comments and handles NoClasspath scenarios defensively.
 * 
 * PLACEHOLDER SCENARIO:
 * Refactoring: com.legacy.Library.oldMethod(String) -> com.modern.Library.newMethod(String)
 */
public class LibraryMigration {

    public static class MethodRefactoringProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check (Fastest check first)
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"oldMethod".equals(methodName)) {
                return false;
            }

            // 2. Argument Count Check
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();
            
            // If we can resolve the type, ensure it matches expectations.
            // If type is null (NoClasspath), we proceed cautiously based on variable names or assume it's correct.
            // Here we assume if it's explicitly NOT a String, we skip.
            if (argType != null && !argType.getQualifiedName().equals("java.lang.String") && !argType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            // 4. Owner/Declaring Type Check (Relaxed string matching)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If owner is known and does NOT match the legacy library, skip it.
            if (declaringType != null && !declaringType.getQualifiedName().contains("Legacy") && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // 1. Define the new owner type
            CtTypeReference<?> newOwnerRef = factory.Type().createReference("com.modern.Library");
            
            // 2. Update the invocation executable reference
            // changing the declaring type and the method name
            invocation.getExecutable().setDeclaringType(newOwnerRef);
            invocation.getExecutable().setSimpleName("newMethod");
            
            // Note: In a static method call, we might also need to update the target expression
            if (invocation.getTarget() != null && invocation.getTarget().getType() != null) {
                // If the code was Legacy.oldMethod(), target is Legacy (type access)
                if (invocation.getTarget().getType().getQualifiedName().contains("Legacy")) {
                   invocation.setTarget(factory.Code().createTypeAccess(newOwnerRef));
                }
            }

            System.out.println("Refactored 'oldMethod' to 'newMethod' at " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/17f2bcaaba4805b218743f575919360c5aec5da4/PGS/src/test/java/micycle/pgs/PGS_MorphologyGroupShapeTests.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/17f2bcaaba4805b218743f575919360c5aec5da4/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/17f2bcaaba4805b218743f575919360c5aec5da4/PGS/src/test/java/micycle/pgs/PGS_MorphologyGroupShapeTests.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/17f2bcaaba4805b218743f575919360c5aec5da4/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES

        // 1. Enable comments to ensure they are preserved
        launcher.getEnvironment().setCommentEnabled(true);

        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Defensive Coding Configuration (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore missing types during model building
        launcher.getEnvironment().setAutoImports(true); 

        launcher.addProcessor(new MethodRefactoringProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}