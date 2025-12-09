package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JaxbStrategyRefactoring {

    public static class StrategyInstanceProcessor extends AbstractProcessor<CtInvocation<?>> {

        // Set of fully qualified names of classes where getInstance() was removed.
        // The diff indicates these strategies no longer support the singleton pattern.
        private static final Set<String> TARGET_CLASSES = new HashSet<>(Arrays.asList(
            "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultCopyStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultEqualsStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultHashCodeStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultMergeStrategy",
            "org.jvnet.jaxb2_commons.lang.DefaultToStringStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBCopyStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBEqualsStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBHashCodeStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBMergeCollectionsStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBMergeStrategy"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"getInstance".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (getInstance() typically takes 0 args for these strategies)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Target Check (must be a static access on a Type, e.g., Strategy.getInstance())
            CtExpression<?> target = candidate.getTarget();
            if (!(target instanceof CtTypeAccess)) {
                return false;
            }

            CtTypeReference<?> typeRef = ((CtTypeAccess<?>) target).getAccessedType();
            if (typeRef == null) {
                return false;
            }

            // 4. Type Matching (Robust for NoClasspath)
            String qName = typeRef.getQualifiedName();
            String simpleName = typeRef.getSimpleName();

            // Check exact FQN match
            if (TARGET_CLASSES.contains(qName)) {
                return true;
            }

            // Check Simple Name match (handles cases where types are imported but not fully resolved in NoClasspath)
            // e.g. Source code uses "JAXBToStringStrategy" directly
            if (!qName.contains(".")) {
                for (String targetClass : TARGET_CLASSES) {
                    if (targetClass.endsWith("." + simpleName)) {
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Context: The diff indicates getInstance() is removed.
            // Strategy: Replace Singleton access `Strategy.getInstance()` with constructor `new Strategy()`.
            
            CtExpression<?> target = invocation.getTarget();
            // We verified target is CtTypeAccess in isToBeProcessed
            CtTypeReference<?> typeRef = ((CtTypeAccess<?>) target).getAccessedType();

            // Create `new StrategyClass()`
            // We use the existing type reference to ensure we use the same import/qualification style as the original code
            CtConstructorCall<?> constructorCall = getFactory().Code().createConstructorCall(typeRef);

            // Replace the original invocation
            invocation.replace(constructorCall);

            System.out.println("Refactored " + typeRef.getSimpleName() + ".getInstance() -> new " + typeRef.getSimpleName() + "() at line " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/46979207151a43361447d64afd2658df40033419/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/services/export/saftpt/v1_03_01/schema/AuditFile.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/46979207151a43361447d64afd2658df40033419/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/46979207151a43361447d64afd2658df40033419/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/services/export/saftpt/v1_03_01/schema/AuditFile.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/46979207151a43361447d64afd2658df40033419/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode to allow running without full dependency tree
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new StrategyInstanceProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}