package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JaxbStrategyRefactoring {

    public static class StrategyInstanceProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // List of strategy classes where getInstance() was removed in favor of INSTANCE field
        private static final Set<String> AFFECTED_CLASSES = new HashSet<>(Arrays.asList(
            "JAXBToStringStrategy", "DefaultCopyStrategy", "DefaultEqualsStrategy",
            "DefaultHashCodeStrategy", "DefaultMergeStrategy", "DefaultToStringStrategy",
            "JAXBCopyStrategy", "JAXBEqualsStrategy", "JAXBHashCodeStrategy",
            "JAXBMergeCollectionsStrategy", "JAXBMergeStrategy"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"getInstance".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (getInstance() had 0 args)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Declaring Type Check
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return false;
            }

            // Defensive Check: NoClasspath safe matching
            String qName = declaringType.getQualifiedName();
            String simpleName = declaringType.getSimpleName();

            // Match if package matches or if simple name is in the known affected list
            boolean isPackageMatch = qName.contains("org.jvnet.jaxb2_commons.lang");
            boolean isClassMatch = AFFECTED_CLASSES.contains(simpleName);

            return isPackageMatch || isClassMatch;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            CtTypeReference<?> strategyType = invocation.getExecutable().getDeclaringType();

            // Transformation: Replace ClassName.getInstance() with ClassName.INSTANCE
            
            // 1. Create Reference to the 'INSTANCE' field
            // We assume the type of the field is the Strategy class itself.
            CtFieldReference<?> instanceRef = factory.Field().createReference(
                strategyType,          // declaring type
                strategyType,          // type of the field
                "INSTANCE"             // field name
            );

            // 2. Determine the target for the field access (the left side of the dot)
            CtExpression<?> targetExpr = invocation.getTarget();
            CtExpression<?> accessTarget;

            if (targetExpr instanceof CtTypeAccess) {
                // If code was explicitly 'DefaultToStringStrategy.getInstance()', keep 'DefaultToStringStrategy'
                accessTarget = targetExpr.clone();
            } else {
                // If code was implicit (static import) or null, explicitly create the TypeAccess
                // to ensure the resulting code 'DefaultToStringStrategy.INSTANCE' is valid.
                accessTarget = factory.Code().createTypeAccess(strategyType);
            }

            // 3. Create the replacement field read expression
            CtExpression<?> fieldRead = factory.Code().createFieldRead(accessTarget, instanceRef);

            // 4. Perform the replacement
            invocation.replace(fieldRead);
            
            System.out.println("Refactored " + strategyType.getSimpleName() + ".getInstance() -> .INSTANCE at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/fe15d2a6e52b049f6c9e7cc123a5402047a1f01a/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/services/export/saftpt/v1_04_01/schema/Supplier.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe15d2a6e52b049f6c9e7cc123a5402047a1f01a/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/fe15d2a6e52b049f6c9e7cc123a5402047a1f01a/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/services/export/saftpt/v1_04_01/schema/Supplier.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/fe15d2a6e52b049f6c9e7cc123a5402047a1f01a/attempt_1/transformed");

        // CRITICAL IMPLEMENTATION RULES
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive Mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StrategyInstanceProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}