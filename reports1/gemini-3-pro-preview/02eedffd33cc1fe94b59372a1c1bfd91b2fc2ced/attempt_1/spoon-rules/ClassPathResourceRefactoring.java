package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class ClassPathResourceRefactoring {

    /**
     * Processor to handle breaking changes in ClassPathResource subclasses.
     * <p>
     * Scenario: Spring 6 added default methods to the Resource interface (e.g., getContentAsByteArray).
     * If a subclass of ClassPathResource already had a method with this name but a different return type,
     * compilation fails due to a clash.
     * <p>
     * Strategy: Detect such collisions and rename the user's method to preserve existing behavior.
     */
    public static class ClassPathResourceConflictProcessor extends AbstractProcessor<CtClass<?>> {

        // The specific method added in Spring 6 Resource interface causing conflicts
        private static final String CONFLICT_METHOD_NAME = "getContentAsByteArray";
        private static final String NEW_METHOD_NAME = "getContentAsByteArrayLegacy";
        private static final String EXPECTED_RETURN_TYPE = "byte[]";

        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            // 1. Check if the class extends ClassPathResource
            CtTypeReference<?> superClass = candidate.getSuperclass();
            if (superClass == null) {
                return false;
            }
            
            // Robust check for NoClasspath environment (relaxed qualified name matching)
            boolean isClassPathResourceSubclass = superClass.getQualifiedName().contains("org.springframework.core.io.ClassPathResource") 
                                               || superClass.getSimpleName().equals("ClassPathResource");
            
            return isClassPathResourceSubclass;
        }

        @Override
        public void process(CtClass<?> ctClass) {
            // 2. Iterate over methods to find collisions
            for (CtMethod<?> method : ctClass.getMethods()) {
                if (CONFLICT_METHOD_NAME.equals(method.getSimpleName())) {
                    processConflictingMethod(method);
                }
            }
        }

        private void processConflictingMethod(CtMethod<?> method) {
            CtTypeReference<?> returnType = method.getType();
            
            // 3. Analyze Return Type
            // If return type is NULL (unknown) we skip to be safe.
            // If return type is byte[], it's a valid override (or just needs @Override), so we leave it.
            // If return type is NOT byte[], it's a collision -> Rename it.
            if (returnType != null && !returnType.toString().equals(EXPECTED_RETURN_TYPE)) {
                
                System.out.println("Refactoring conflict in class: " + method.getDeclaringType().getQualifiedName());
                System.out.println("Renaming method " + method.getSignature() + " to " + NEW_METHOD_NAME);

                // 4. Rename references (Invocations)
                // In NoClasspath, getReferences() relies on the AST model.
                // We scan the model for invocations matching this method name/arg-count as a best-effort heuristic,
                // or rely on Spoon's reference linkage if available.
                updateReferences(method);

                // 5. Rename the declaration
                method.setSimpleName(NEW_METHOD_NAME);
            }
        }

        private void updateReferences(CtMethod<?> method) {
            // Find all references to this method in the model
            List<CtInvocation<?>> invocations = Query.getElements(method.getFactory(), new TypeFilter<>(CtInvocation.class));
            
            for (CtInvocation<?> invocation : invocations) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                
                // Match by name and argument count (defensive matching)
                if (CONFLICT_METHOD_NAME.equals(execRef.getSimpleName()) 
                    && execRef.getParameters().size() == method.getParameters().size()) {
                    
                    // In NoClasspath, we can't always resolve the declaring type perfectly.
                    // We check if the invocation target might be compatible with the refactored class.
                    // If it's an internal call (this.method()), it's safe to rename.
                    
                    // Simple logic: update the reference name
                    execRef.setSimpleName(NEW_METHOD_NAME);
                }
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden)
        String inputPath = "/home/kth/Documents/last_transformer/output/02eedffd33cc1fe94b59372a1c1bfd91b2fc2ced/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/02eedffd33cc1fe94b59372a1c1bfd91b2fc2ced/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/02eedffd33cc1fe94b59372a1c1bfd91b2fc2ced/IDS-Messaging-Services/core/src/main/java/ids/messaging/core/config/ssl/keystore/KeyStoreManager.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/02eedffd33cc1fe94b59372a1c1bfd91b2fc2ced/attempt_1/transformed");

        // CRITICAL SETTINGS for robust refactoring
        // 1. Enable comments to preserve file headers and Javadoc
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. NoClasspath mode allows running without all dependencies present
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new ClassPathResourceConflictProcessor());

        try {
            System.out.println("Starting ClassPathResource Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}