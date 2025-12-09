package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TinSpinRefactoring {

    /**
     * Processor to handle method renames:
     * query1NN -> query1nn
     */
    public static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        // Known classes where query1NN was removed/renamed
        private static final Set<String> TARGET_CLASSES = new HashSet<>(Arrays.asList(
            "KDTree", "PointIndex", "RectangleIndex", "CoverTree", "RTree", 
            "PointIndexWrapper", "PointMapWrapper", "PointMultimapWrapper"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            if (!"query1NN".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Defensive Type Check (NoClasspath friendly)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            
            // If type is completely unknown, we often process unique method names to be safe.
            // If type is known, we check if it matches target classes or package.
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                String simpleName = declaringType.getSimpleName();

                // If explicitly a different package, skip
                if (!qName.equals("<unknown>") && !qName.contains("tinspin")) {
                    return false;
                }

                // If simple name is known to be a target class
                if (TARGET_CLASSES.contains(simpleName)) {
                    return true;
                }
                
                // If it's a generic reference in the package
                return qName.contains("tinspin");
            }
            
            // Fallback for unknown types with matching method name
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            invocation.getExecutable().setSimpleName("query1nn");
            System.out.println("Refactored Method: query1NN -> query1nn at line " + invocation.getPosition().getLine());
        }
    }

    /**
     * Processor to handle Class and Interface Renames:
     * PointDistanceFunction -> PointDistance
     * RectangleDistanceFunction -> BoxDistance
     * RTreeQuery1NN -> RTreeQuery1nn
     */
    public static class TypeRenameProcessor extends AbstractProcessor<CtTypeReference<?>> {

        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Filter out implicit references (e.g. implied by imports) to focus on explicit code usage
            if (candidate.isImplicit()) return false;

            String name = candidate.getSimpleName();
            return "PointDistanceFunction".equals(name) ||
                   "RectangleDistanceFunction".equals(name) ||
                   "RTreeQuery1NN".equals(name);
        }

        @Override
        public void process(CtTypeReference<?> ref) {
            String oldName = ref.getSimpleName();
            String newName = null;

            switch (oldName) {
                case "PointDistanceFunction":
                    newName = "PointDistance";
                    break;
                case "RectangleDistanceFunction":
                    newName = "BoxDistance";
                    break;
                case "RTreeQuery1NN":
                    newName = "RTreeQuery1nn";
                    break;
            }

            if (newName != null) {
                ref.setSimpleName(newName);
                System.out.println("Refactored Type: " + oldName + " -> " + newName + " at line " + ref.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ef3f7be3e2755d4a0f9c23bdcbfe3b97198fb31b/PGS/src/main/java/micycle/pgs/PGS_PointSet.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ef3f7be3e2755d4a0f9c23bdcbfe3b97198fb31b/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ef3f7be3e2755d4a0f9c23bdcbfe3b97198fb31b/PGS/src/main/java/micycle/pgs/PGS_PointSet.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ef3f7be3e2755d4a0f9c23bdcbfe3b97198fb31b/attempt_1/transformed");

        // CRITICAL: Robust Sniper Configuration for Preserving Formatting
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Set NoClasspath to true to handle partial source code
        launcher.getEnvironment().setNoClasspath(true);

        // Register Processors
        launcher.addProcessor(new MethodRenameProcessor());
        launcher.addProcessor(new TypeRenameProcessor());

        System.out.println("Starting Refactoring...");
        try {
            launcher.run();
            System.out.println("Refactoring Complete. Output in: " + outputPath);
        } catch (Exception e) {
            System.err.println("Error during refactoring:");
            e.printStackTrace();
        }
    }
}