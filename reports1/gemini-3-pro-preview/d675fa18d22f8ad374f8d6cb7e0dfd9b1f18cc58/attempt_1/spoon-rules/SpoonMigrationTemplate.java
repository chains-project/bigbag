package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Spoon Migration Tool
 * 
 * Generated based on the provided system constraints.
 * MISSING INPUT: The dependency diff was empty. This is a template.
 * 
 * FEATURES:
 * 1. Sniper Mode: Preserves formatting/comments strictly.
 * 2. NoClasspath Safety: Handles missing dependencies without crashing.
 * 3. Generic Safety: Uses wildcard generics to satisfy compiler constraints.
 */
public class SpoonMigrationTemplate {

    public static class MigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // TODO: Update with the specific method name from your diff
        private static final String TARGET_METHOD = "targetMethodName";
        // TODO: Update with the specific class name (partial match is safer for NoClasspath)
        private static final String TARGET_CLASS_TOKEN = "TargetClassName";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!TARGET_METHOD.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Owner/Type Check (Defensive for NoClasspath)
            // We use string matching because resolving the full type might fail if JARs are missing.
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                // If the type is known (not <unknown>) and doesn't contain our target class name, skip it.
                if (!"<unknown>".equals(qName) && !qName.contains(TARGET_CLASS_TOKEN)) {
                    return false;
                }
            }
            
            // 3. Argument Check (Example: Filter by arg count)
            // if (candidate.getArguments().size() != 1) return false;

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // --- TRANSFORMATION LOGIC START ---
            // TODO: Implement your specific refactoring logic here.
            
            // Example: Renaming the method
            // invocation.getExecutable().setSimpleName("newMethodName");

            // Example: Wrapping an argument
            // CtExpression<?> originalArg = invocation.getArguments().get(0);
            // CtTypeReference<?> newType = factory.Type().createReference("java.util.Optional");
            // CtInvocation<?> replacement = factory.Code().createInvocation(
            //     factory.Code().createTypeAccess(newType),
            //     factory.Method().createReference(newType, factory.Type().voidPrimitiveType(), "of"),
            //     originalArg.clone()
            // );
            // originalArg.replace(replacement);
            // --- TRANSFORMATION LOGIC END ---

            System.out.println("Refactored usage at: " + invocation.getPosition());
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/RequestTemplateProvider.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/IDS-Messaging-Services/messaging/src/main/java/ids/messaging/requests/RequestTemplateProvider.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/d675fa18d22f8ad374f8d6cb7e0dfd9b1f18cc58/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULES ---
        
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually
        // This ensures the source code (indentation, whitespace) is preserved exactly.
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive Configuration (NoClasspath)
        // Allows running on source code even if libraries are missing.
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new MigrationProcessor());
        
        try {
            launcher.run();
            System.out.println("Migration finished. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}