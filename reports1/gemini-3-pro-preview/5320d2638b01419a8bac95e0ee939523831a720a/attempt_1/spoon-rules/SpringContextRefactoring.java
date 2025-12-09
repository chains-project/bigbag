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

public class SpringContextRefactoring {

    /**
     * Processor to handle breaking changes in ConfigurableApplicationContext.
     * The diff indicates source incompatibility for ConfigurableApplicationContext,
     * specifically around getBeanFactory().
     *
     * Strategy: Ensure calls to getBeanFactory() are explicitly performed on
     * ConfigurableApplicationContext to resolve potential ambiguity or visibility issues
     * arising from the source incompatibility.
     */
    public static class GetBeanFactoryCastProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"getBeanFactory".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (getBeanFactory has 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Target Check
            CtExpression<?> target = candidate.getTarget();
            if (target == null) {
                // Implicit 'this' or static call, skip for safety in NoClasspath
                return false;
            }

            // 4. Type Check (Defensive for NoClasspath)
            CtTypeReference<?> typeRef = target.getType();
            
            // If the target is already known to be ConfigurableApplicationContext, skip.
            // In NoClasspath, typeRef might be null, so we process aggressively or check heuristic names.
            if (typeRef != null && typeRef.getQualifiedName().contains("ConfigurableApplicationContext")) {
                return false;
            }

            // 5. Check if it's already casted (avoid double casting)
            if (target instanceof CtUnaryOperator) {
                CtUnaryOperator<?> op = (CtUnaryOperator<?>) target;
                if (op.getKind() == UnaryOperatorKind.CAST) {
                    CtTypeReference<?> castType = op.getType();
                    if (castType != null && castType.getQualifiedName().contains("ConfigurableApplicationContext")) {
                        return false;
                    }
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtExpression<?> originalTarget = invocation.getTarget();

            // Create the reference for the cast type
            CtTypeReference<?> configContextRef = factory.Type().createReference("org.springframework.context.ConfigurableApplicationContext");

            // Transformation: ((ConfigurableApplicationContext) originalTarget).getBeanFactory()
            // We clone originalTarget to detach it from the current tree before re-attaching
            CtUnaryOperator<?> castedTarget = factory.Code().createTypeCast(originalTarget.clone(), configContextRef);
            
            // Ensure parenthesis are applied: ((Type) var)
            castedTarget.setParenthesized(true);

            // Apply replacement
            invocation.setTarget(castedTarget);
            
            System.out.println("Refactored getBeanFactory call at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/5320d2638b01419a8bac95e0ee939523831a720a/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5320d2638b01419a8bac95e0ee939523831a720a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/5320d2638b01419a8bac95e0ee939523831a720a/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5320d2638b01419a8bac95e0ee939523831a720a/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for strict source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Defensive Coding (NoClasspath Compatibility)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new GetBeanFactoryCastProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}