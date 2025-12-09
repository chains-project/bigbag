package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring Rule for ConfigurableApplicationContext source incompatibility.
 * 
 * Logic:
 * The diff indicates ConfigurableApplicationContext is source incompatible, 
 * likely due to type hierarchy changes affecting return type inference.
 * We enforce an explicit cast to 'ConfigurableListableBeanFactory' on 
 * calls to 'getBeanFactory()' to ensure compilation safety.
 */
public class SpringContextRefactoring {

    public static class BeanFactoryCastProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        private static final String METHOD_NAME = "getBeanFactory";
        private static final String OWNER_CLASS = "ConfigurableApplicationContext";
        private static final String TARGET_CAST_TYPE = "org.springframework.beans.factory.config.ConfigurableListableBeanFactory";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!METHOD_NAME.equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (getBeanFactory() takes 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Owner Check (Defensive string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null && !owner.getQualifiedName().contains(OWNER_CLASS)) {
                return false;
            }

            // 4. Redundancy Check: Is it already casted?
            // e.g., (ConfigurableListableBeanFactory) context.getBeanFactory()
            if (candidate.getParent() instanceof CtUnaryOperator) {
                CtUnaryOperator<?> op = (CtUnaryOperator<?>) candidate.getParent();
                if (op.getKind() == UnaryOperatorKind.CAST) {
                    CtTypeReference<?> castType = op.getType();
                    if (castType != null && castType.getQualifiedName().equals(TARGET_CAST_TYPE)) {
                        return false; // Already explicitly casted
                    }
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Define the type we want to cast to
            CtTypeReference<?> targetTypeRef = factory.Type().createReference(TARGET_CAST_TYPE);

            // Transformation: Wrap the existing invocation in a Type Cast
            // Original: context.getBeanFactory()
            // New: ((ConfigurableListableBeanFactory) context.getBeanFactory())
            
            // Note: createTypeCast automatically clones the operand if passed correctly, 
            // but we clone explicitly to ensure tree integrity before replacement.
            CtUnaryOperator<?> castExpression = factory.Code().createTypeCast(
                invocation.clone(),
                targetTypeRef
            );

            invocation.replace(castExpression);
            
            System.out.println("Applied explicit cast for source compatibility at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/11be71ab8713fe987785e9e25e4f3e410e709ab9/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/11be71ab8713fe987785e9e25e4f3e410e709ab9/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/11be71ab8713fe987785e9e25e4f3e410e709ab9/camunda-platform-7-mockito/src/main/java/org/camunda/community/mockito/process/CallActivityMockForSpringContext.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/11be71ab8713fe987785e9e25e4f3e410e709ab9/attempt_1/transformed");

        // CRITICAL SETTINGS for Sniper (Preserve Formatting)
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (Defensive processing)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new BeanFactoryCastProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}