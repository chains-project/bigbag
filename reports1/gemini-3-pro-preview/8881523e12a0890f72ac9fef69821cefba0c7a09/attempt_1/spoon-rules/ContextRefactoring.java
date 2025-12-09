package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class ContextRefactoring {

    /**
     * Refactoring Processor:
     * Handles the migration for Spring Context's getBeanFactory().
     * 
     * Analysis of Diff:
     * - ConfigurableApplicationContext is marked as MODIFIED/Source-Incompatible.
     * - getBeanFactory() is unchanged but involved in the context of the change.
     * 
     * Strategy:
     * Identify calls to `getBeanFactory()`. Ensure they are invoked on `ConfigurableApplicationContext`.
     * If invoked on `ApplicationContext` (or unknown types in NoClasspath), explicit casting is required
     * to resolve compilation issues arising from source incompatibility or hierarchy changes.
     * 
     * Transformation:
     * context.getBeanFactory() -> ((ConfigurableApplicationContext) context).getBeanFactory()
     */
    public static class BeanFactoryCastProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final String METHOD_NAME = "getBeanFactory";
        private static final String TARGET_TYPE = "org.springframework.context.ConfigurableApplicationContext";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            if (!METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Argument Count (getBeanFactory usually has 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Analyze Target Expression
            CtExpression<?> target = candidate.getTarget();

            if (target != null) {
                // Defensive check: If it's already cast to the correct type, skip it.
                if (target instanceof CtUnaryOperator) {
                    CtUnaryOperator<?> op = (CtUnaryOperator<?>) target;
                    if (op.getKind() == UnaryOperatorKind.CAST) {
                        CtTypeReference<?> castType = op.getCastType();
                        if (castType != null && castType.getQualifiedName().contains("ConfigurableApplicationContext")) {
                            return false; // Already fixed
                        }
                    }
                }

                // Defensive check: If the variable type is already known to be ConfigurableApplicationContext, skip.
                CtTypeReference<?> type = target.getType();
                if (type != null && type.getQualifiedName().contains("ConfigurableApplicationContext")) {
                    return false;
                }
            }
            
            // If we are here, the target is either:
            // a) Implicit 'this' (target == null)
            // b) Explicit variable of type ApplicationContext (or unknown)
            // In both cases, we enforce the cast to ensure source compatibility.
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalTarget = invocation.getTarget();

            // 1. Create Reference for the Cast Type
            CtTypeReference<?> configContextRef = factory.Type().createReference(TARGET_TYPE);

            // 2. Handle Implicit 'this'
            if (originalTarget == null) {
                // If call was implicit 'getBeanFactory()', we treat it as 'this'
                // We create a loose 'this' access.
                originalTarget = factory.Code().createThisAccess(null);
            }

            // 3. Create the Cast: ((ConfigurableApplicationContext) target)
            // Cloning originalTarget is safer to detach it from current parent before re-parenting in cast
            CtExpression<?> castExpression = factory.Code().createTypeCast(originalTarget.clone(), configContextRef);

            // 4. Apply Replacement
            // Spoon handles the parentheses precedence automatically when setting the target of an invocation
            invocation.setTarget(castExpression);

            System.out.println("Refactored getBeanFactory() call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/8881523e12a0890f72ac9fef69821cefba0c7a09/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/8881523e12a0890f72ac9fef69821cefba0c7a09/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/8881523e12a0890f72ac9fef69821cefba0c7a09/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/8881523e12a0890f72ac9fef69821cefba0c7a09/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES (Do not violate)
        
        // 1. Preserve Source Code (Robust Sniper Configuration)
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // 2. Defensive Coding (NoClasspath Compatibility)
        // Set NoClasspath to true to handle partial source code
        launcher.getEnvironment().setNoClasspath(true);
        // Ignore missing types/methods during model building
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        launcher.addProcessor(new BeanFactoryCastProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}