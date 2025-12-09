package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JAXBStrategyRefactoring {

    public static class JAXBStrategyProcessor extends AbstractProcessor<CtInvocation<?>> {

        // List of classes where getInstance() was removed and should likely be replaced by .INSTANCE
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
            // 1. Check Method Name
            if (!"getInstance".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Argument Count (getInstance() usually has 0 args in this context)
            if (!candidate.getArguments().isEmpty()) {
                return false;
            }

            // 3. Check Owner Type (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If we can't determine the type, we skip to avoid false positives (like Calendar.getInstance)
            if (declaringType == null) {
                return false;
            }

            String qualifiedName = declaringType.getQualifiedName();
            String simpleName = declaringType.getSimpleName();

            // Check against full names or simple names (for robustness in NoClasspath)
            boolean isTarget = TARGET_CLASSES.contains(qualifiedName) || 
                               TARGET_CLASSES.stream().anyMatch(t -> t.endsWith("." + simpleName));
            
            return isTarget;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            CtTypeReference<?> ownerType = invocation.getExecutable().getDeclaringType();
            
            // Defensive: ensure we have a valid reference to build the replacement
            if (ownerType == null) {
                return;
            }

            // Transformation: Replace ClassName.getInstance() with ClassName.INSTANCE
            // 1. Create reference to the 'INSTANCE' field
            // Note: We assume the field type is the same as the owner type (Singleton pattern)
            CtFieldReference<?> instanceFieldRef = getFactory().Field().createReference(
                ownerType,
                ownerType,
                "INSTANCE"
            );

            // 2. Create the field read access: ClassName.INSTANCE
            CtFieldRead<?> fieldRead = getFactory().Code().createFieldRead(
                getFactory().Code().createTypeAccess(ownerType),
                instanceFieldRef
            );

            // 3. Replace the invocation
            invocation.replace(fieldRead);
            
            System.out.println("Refactored " + ownerType.getSimpleName() + ".getInstance() to .INSTANCE at line " 
                + (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Allow user to provide input path via args, default to standard Maven layout
        String inputPath = "/home/kth/Documents/last_transformer/output/46979207151a43361447d64afd2658df40033419/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/services/export/saftpt/v1_03_01/schema/SourceDocuments.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/46979207151a43361447d64afd2658df40033419/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/46979207151a43361447d64afd2658df40033419/billy/billy-portugal/src-generated/main/java/com/premiumminds/billy/portugal/services/export/saftpt/v1_03_01/schema/SourceDocuments.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/46979207151a43361447d64afd2658df40033419/attempt_1/transformed");

        // CRITICAL: Preserve formatting and comments using Sniper
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // Defensive: Handle missing libraries
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new JAXBStrategyProcessor());

        try {
            System.out.println("Starting JAXB Strategy Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}