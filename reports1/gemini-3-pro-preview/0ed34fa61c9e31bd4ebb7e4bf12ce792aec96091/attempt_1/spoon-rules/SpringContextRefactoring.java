package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Refactoring rule for Spring's ConfigurableApplicationContext changes.
 * 
 * Based on the diff:
 * - ConfigurableApplicationContext is SourceCompatible=false (modified).
 * - getBeanFactory() is flagged (status=UNCHANGED, but context implies breakage).
 * 
 * Strategy:
 * In scenarios where Source Compatibility is broken but Binary Compatibility is preserved,
 * it often implies changes in inheritance or generic type inference that confuse the compiler.
 * This processor ensures that calls to `getBeanFactory()` are explicitly cast to 
 * `ConfigurableApplicationContext` to resolve potential ambiguity.
 * 
 * Transformation:
 * ctx.getBeanFactory() -> ((ConfigurableApplicationContext) ctx).getBeanFactory()
 */
public class SpringContextRefactoring {

    public static class BeanFactoryProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String TARGET_METHOD = "getBeanFactory";
        private static final String TARGET_TYPE = "ConfigurableApplicationContext";
        private static final String FULL_TARGET_TYPE = "org.springframework.context.ConfigurableApplicationContext";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!TARGET_METHOD.equals(methodName)) {
                return false;
            }

            // 2. Check Argument Count (getBeanFactory takes 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Defensive Type Check (NoClasspath safe)
            CtExpression<?> target = candidate.getTarget();
            if (target == null) {
                return false; // Static call or implicit 'this' (skip for safety if ambiguous)
            }

            // Check if the declaring type of the method is likely the one we care about
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null && !declaringType.getQualifiedName().contains(TARGET_TYPE) 
                && !declaringType.getQualifiedName().equals("<unknown>")) {
                return false;
            }
            
            // Optimization: If it's already explicitly cast to the target type in the source, skip.
            // (This is hard to detect perfectly in NoClasspath without full AST analysis of parent, 
            // but we can check if the target expression is a CtUnaryOperator casting to our type).
            // For this implementation, we apply the fix if the type is ambiguous or matches.
            
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalTarget = invocation.getTarget();
            
            // Create reference to org.springframework.context.ConfigurableApplicationContext
            CtTypeReference<?> contextTypeRef = factory.Type().createReference(FULL_TARGET_TYPE);

            // Defensive: Check if the target is already typed correctly and we are in a fully resolved environment.
            // In NoClasspath, getType() might be null or inaccurate, so we lean towards applying the cast 
            // if we matched the method name and potential owner.
            CtTypeReference<?> targetType = originalTarget.getType();
            if (targetType != null && targetType.getQualifiedName().equals(FULL_TARGET_TYPE)) {
                // If we are 100% sure it's already that type, strictly speaking we might not need a cast,
                // but if SourceCompatible=false, explicit casting fixes inference issues.
                // We proceed.
            }

            // Transformation: ((ConfigurableApplicationContext) originalTarget).getBeanFactory()
            // 1. Clone the original target to preserve it in the new tree
            CtExpression<?> clonedTarget = originalTarget.clone();
            
            // 2. Create the cast expression
            CtExpression<?> castedTarget = factory.Code().createTypeCast(contextTypeRef, clonedTarget);
            
            // 3. Replace the invocation's target
            // Note: We modify the invocation in place by setting the target, 
            // rather than creating a new Invocation, to preserve surrounding context/comments better.
            invocation.setTarget(castedTarget);
            
            System.out.println("Applied explicit cast to getBeanFactory at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default Configuration
        String inputPath = "/home/kth/Documents/last_transformer/output/0ed34fa61c9e31bd4ebb7e4bf12ce792aec96091/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ed34fa61c9e31bd4ebb7e4bf12ce792aec96091/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0ed34fa61c9e31bd4ebb7e4bf12ce792aec96091/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0ed34fa61c9e31bd4ebb7e4bf12ce792aec96091/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: Sniper Configuration for Source Preservation
        // ==========================================================
        // 1. Enable comments recording
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Set the PrettyPrinter to Sniper manually
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 3. Enable NoClasspath mode (Defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new BeanFactoryProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete.");
        } catch (Exception e) {
            System.err.println("Refactoring failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}