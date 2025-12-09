package org.tinspin.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.List;

public class SizeMigration {

    /**
     * Processor to migrate public field access 'size' to method call 'size()'.
     * Affected classes: PointIndexMMWrapper, PHTreeMMP, MinHeap, MinHeapI, MinMaxHeap, MinMaxHeapI.
     */
    public static class SizeFieldToMethodProcessor extends AbstractProcessor<CtFieldRead<?>> {

        private static final List<String> TARGET_TYPES = Arrays.asList(
            "org.tinspin.index.PointIndexMMWrapper",
            "org.tinspin.index.phtree.PHTreeMMP",
            "org.tinspin.index.util.MinHeap",
            "org.tinspin.index.util.MinHeapI",
            "org.tinspin.index.util.MinMaxHeap",
            "org.tinspin.index.util.MinMaxHeapI"
        );

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Check Field Name
            if (!"size".equals(candidate.getVariable().getSimpleName())) {
                return false;
            }

            // 2. Defensive Check for Target
            CtExpression<?> target = candidate.getTarget();
            if (target == null) {
                return false;
            }

            // 3. Type Resolution (Defensive for NoClasspath)
            CtTypeReference<?> typeRef = target.getType();
            if (typeRef == null) {
                return false;
            }

            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName == null || qualifiedName.equals("<unknown>")) {
                return false;
            }

            // 4. Check if the type belongs to the affected list
            // Using contains to handle Generics (e.g. MinHeap<T>)
            for (String targetType : TARGET_TYPES) {
                if (qualifiedName.contains(targetType)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            Factory factory = getFactory();
            CtExpression<?> target = fieldRead.getTarget();

            // Create reference to the new size() method
            // We use the type of the target to ensure the method reference belongs to the correct class
            CtTypeReference<?> targetType = target.getType();
            CtExecutableReference<?> sizeMethodRef = factory.Method().createReference(
                targetType,
                factory.Type().integerPrimitiveType(),
                "size"
            );

            // Create invocation: target.size()
            CtInvocation<?> newInvocation = factory.Code().createInvocation(
                target.clone(), // Clone target to preserve its state/comments
                sizeMethodRef
            );

            // Replace the field read with the method invocation
            fieldRead.replace(newInvocation);

            System.out.println("Refactored field access 'size' to 'size()' at line " + 
                (fieldRead.getPosition().isValidPosition() ? fieldRead.getPosition().getLine() : "?"));
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/17f2bcaaba4805b218743f575919360c5aec5da4/PGS/src/test/java/micycle/pgs/PGS_MeshingTests.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/17f2bcaaba4805b218743f575919360c5aec5da4/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/17f2bcaaba4805b218743f575919360c5aec5da4/PGS/src/test/java/micycle/pgs/PGS_MeshingTests.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/17f2bcaaba4805b218743f575919360c5aec5da4/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments to preserve existing code documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to respect original formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new SizeFieldToMethodProcessor());

        try {
            System.out.println("Starting Refactoring: Field 'size' -> Method 'size()'...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}