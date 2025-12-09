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
 * Refactoring rule for org.springframework.util.StringUtils.
 * 
 * Change:
 * - org.springframework.util.StringUtils [MODIFIED]
 * 
 * Context:
 * In newer Spring versions (e.g., migration to Spring Framework 6), 
 * StringUtils.isEmpty(Object) is removed/deprecated. 
 * It should be replaced by org.springframework.util.ObjectUtils.isEmpty(Object).
 */
public class SpringStringUtilsRefactoring {

    public static class StringUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            // We are looking for "isEmpty".
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"isEmpty".equals(methodName)) {
                return false;
            }

            // 2. Argument Check
            // StringUtils.isEmpty(Object) takes exactly 1 argument.
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner/Declaring Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // In NoClasspath, references might be partial. We check for the simple name or qualified name.
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                // Check if it belongs to StringUtils (fully qualified or simple name)
                if (!typeName.contains("StringUtils")) {
                    return false;
                }
                // Determine if it is likely the Spring StringUtils
                // If the package is known (even partially), verify it is NOT apache commons (which also has StringUtils)
                // If it's strictly "StringUtils" (unqualified), we assume it matches the context of the migration 
                // unless we have evidence otherwise.
                if (typeName.contains("apache")) {
                    return false;
                }
            } else {
                // If we can't resolve the declaring type, we skip to avoid false positives.
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();

            // We want to switch from StringUtils.isEmpty -> ObjectUtils.isEmpty
            // Create a reference to org.springframework.util.ObjectUtils
            CtTypeReference<?> objectUtilsRef = factory.Type().createReference("org.springframework.util.ObjectUtils");
            
            // Create the TypeAccess expression (i.e., "ObjectUtils")
            CtExpression<?> newTarget = factory.Code().createTypeAccess(objectUtilsRef);

            // Update the invocation target
            // This transforms "StringUtils.isEmpty(x)" to "ObjectUtils.isEmpty(x)"
            // It also handles cases where the target was implicit (static import), making it explicit.
            invocation.setTarget(newTarget);
            
            // Note: Since the method name "isEmpty" is the same in ObjectUtils, 
            // we do not need to rename the executable reference, just the target class.
            
            System.out.println("Refactored StringUtils.isEmpty to ObjectUtils.isEmpty at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/aa14451c6f218af9c08e846345d83259eb7d46a8/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/aa14451c6f218af9c08e846345d83259eb7d46a8/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/aa14451c6f218af9c08e846345d83259eb7d46a8/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/aa14451c6f218af9c08e846345d83259eb7d46a8/attempt_1/transformed");

        // CRITICAL SETTINGS for Robust Refactoring
        // 1. Enable comments to preserve them during parsing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually
        // This ensures that only the AST nodes we modify are reprinted, 
        // preserving formatting of unrelated lines.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. NoClasspath Mode
        // Allows running without full dependency resolution
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StringUtilsProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}