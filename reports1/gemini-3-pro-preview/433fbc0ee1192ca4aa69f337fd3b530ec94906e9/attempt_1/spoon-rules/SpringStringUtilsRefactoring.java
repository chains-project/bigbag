package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Rule for Spring Framework 6.0 StringUtils.
 * 
 * Breaking Change:
 * - METHOD org.springframework.util.StringUtils.isEmpty(Object) [REMOVED]
 * 
 * Migration Strategy:
 * - Original: StringUtils.isEmpty(arg)
 * - Replacement: !StringUtils.hasLength(arg)
 */
public class SpringStringUtilsRefactoring {

    public static class StringUtilsProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"isEmpty".equals(methodName)) {
                return false;
            }

            // 2. Check Argument Count (isEmpty took exactly 1 arg)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner Class (Defensive for NoClasspath)
            // We check if the method belongs to a class named "StringUtils"
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return false;
            }
            
            String qualifiedName = declaringType.getQualifiedName();
            // Match "org.springframework.util.StringUtils" or just "StringUtils" if imports are simple
            if (!qualifiedName.contains("StringUtils")) {
                return false;
            }

            // Optional: You could check if imports contain Spring, but usually name matching is sufficient
            // for migration scripts within a known Spring project.
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalArg = invocation.getArguments().get(0);
            
            // We need to transform: StringUtils.isEmpty(x) -> !StringUtils.hasLength(x)

            // 1. Create Reference to StringUtils
            CtTypeReference<?> stringUtilsRef = factory.Type().createReference("org.springframework.util.StringUtils");

            // 2. Create the invocation: StringUtils.hasLength(x)
            // Note: hasLength takes a String. In NoClasspath, we assume 'originalArg' is compatible.
            CtInvocation<?> hasLengthInvocation = factory.Code().createInvocation(
                factory.Code().createTypeAccess(stringUtilsRef),
                factory.Method().createReference(
                    stringUtilsRef, 
                    factory.Type().booleanPrimitiveType(), 
                    "hasLength", 
                    factory.Type().stringType()
                ),
                originalArg.clone() // Clone argument to detach from old parent
            );

            // 3. Wrap in Negation (!)
            CtUnaryOperator<?> negatedExpression = factory.Code().createUnaryOperator(
                UnaryOperatorKind.NOT, 
                hasLengthInvocation
            );

            // 4. Replace the original invocation
            invocation.replace(negatedExpression);

            System.out.println("Refactored StringUtils.isEmpty at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default Configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/LPVS/src/main/java/com/lpvs/controller/GitHubWebhooksController.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/433fbc0ee1192ca4aa69f337fd3b530ec94906e9/attempt_1/transformed");

        // --- CRITICAL SNIPER CONFIGURATION ---
        // 1. Enable comments to prevent loss during printing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force SniperJavaPrettyPrinter to preserve original formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. NoClasspath Mode (Robustness against missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);
        // -------------------------------------

        launcher.addProcessor(new StringUtilsProcessor());

        try {
            System.out.println("Starting Spring StringUtils Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}