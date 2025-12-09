package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PitestRefactoring {

    /**
     * Processor to handle the removal of getClassInfo(Collection) from ReportCoverage and its implementations.
     * Since the method is removed without a direct 1:1 replacement in the diff, 
     * this processor replaces the call with 'null' and a FIXME comment to allow compilation 
     * while alerting the developer.
     */
    public static class ReportCoverageProcessor extends AbstractProcessor<CtInvocation<?>> {

        private static final Set<String> TARGET_CLASSES = new HashSet<>(Arrays.asList(
            "ReportCoverage",
            "CoverageData",
            "LegacyClassCoverage",
            "NoCoverage"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            if (!"getClassInfo".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Check Argument Count (The removed method took 1 argument: Collection)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Check Owner Type (Defensive for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner != null) {
                String ownerName = owner.getQualifiedName();
                boolean isTarget = false;
                // Check if the owner matches any of the affected PITest classes
                for (String target : TARGET_CLASSES) {
                    if (ownerName.contains(target)) {
                        isTarget = true;
                        break;
                    }
                }
                // If the type is completely unknown, we might process it if args match, 
                // but usually we want some confirmation of the type. 
                // However, <unknown> is common in NoClasspath.
                if (!isTarget && !ownerName.equals("<unknown>")) {
                    return false;
                }
            }

            // 4. Check Argument Type (Defensive)
            CtExpression<?> arg = candidate.getArguments().get(0);
            CtTypeReference<?> argType = arg.getType();
            if (argType != null) {
                // We are looking for java.util.Collection or List or Set.
                String argTypeName = argType.getQualifiedName();
                // If it's explicitly NOT a collection type (e.g. it's a String), skip.
                // But in NoClasspath, be permissive if unsure.
                boolean likelyCollection = argTypeName.contains("Collection") 
                                        || argTypeName.contains("List") 
                                        || argTypeName.contains("Set");
                
                // If we are sure it's NOT a collection (and not unknown), skip.
                if (!likelyCollection && !argTypeName.equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Logic: The API was removed. To fix the build, we replace the call 
            // with 'null' and append a comment explaining the removal.
            // This is safer than deleting the line (which might break variable assignment).
            
            CtCodeSnippetExpression<?> replacement = factory.Code().createCodeSnippetExpression(
                "null /* FIXME: PITest API Removed: getClassInfo(Collection) was removed. Please check migration guide. */"
            );

            invocation.replace(replacement);
            
            System.out.println("Refactored getClassInfo removal at " + 
                (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "unknown line"));
        }
    }

    public static void main(String[] args) {
        // Standard Launcher setup
        Launcher launcher = new Launcher();
        
        // Adjust paths as needed
        String inputPath = "/home/kth/Documents/last_transformer/output/5fcd0c3ad7727850c47602b17530dc355e5bd097/pitest-mutation-testing-elements-plugin/src/main/java/org/pitest/elements/MutationReportListener.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5fcd0c3ad7727850c47602b17530dc355e5bd097/attempt_1/transformed";
        
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/5fcd0c3ad7727850c47602b17530dc355e5bd097/pitest-mutation-testing-elements-plugin/src/main/java/org/pitest/elements/MutationReportListener.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/5fcd0c3ad7727850c47602b17530dc355e5bd097/attempt_1/transformed");

        // CRITICAL: Configure Environment for Robust Sniper Mode
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Set NoClasspath to true (Defensive mode)
        launcher.getEnvironment().setNoClasspath(true);
        // 3. Force SniperJavaPrettyPrinter to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );

        // Add the processor
        launcher.addProcessor(new ReportCoverageProcessor());

        try {
            System.out.println("Starting Refactoring...");
            launcher.run();
            System.out.println("Refactoring Complete. Check " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}