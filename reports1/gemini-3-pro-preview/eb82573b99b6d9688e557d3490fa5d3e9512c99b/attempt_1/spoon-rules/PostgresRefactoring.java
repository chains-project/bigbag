package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PostgresRefactoring {

    /**
     * Processor to handle breaking changes in AbstractDatabase and PostgresDatabase.
     * Detects methods flagged as removed/modified in the superclass and adds FIXME comments.
     */
    public static class DatabaseMethodProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // Set of method names identified in the dependency diff as problematic/removed
        private static final Set<String> TARGET_METHODS = new HashSet<>(Arrays.asList(
            "getProperties",
            "getBasedir",
            "setConnection",
            "close"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!TARGET_METHODS.contains(methodName)) {
                return false;
            }

            // 2. Check Declaring Type (Defensive for NoClasspath)
            CtExecutableReference<?> exec = candidate.getExecutable();
            CtTypeReference<?> declaringType = exec.getDeclaringType();

            // Use string matching to handle incomplete classpath resolution
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                boolean isJooqDb = typeName.contains("org.jooq.meta.AbstractDatabase") 
                                || typeName.contains("org.jooq.meta.postgres.PostgresDatabase");
                
                // If it's explicitly NOT one of our targets, skip.
                // Note: In NoClasspath, getQualifiedName might be just "AbstractDatabase" or "<unknown>"
                // We process if it matches known types or if we suspect it based on name + context
                if (!isJooqDb && !typeName.equals("<unknown>")) {
                    return false;
                }
            }

            // 3. Argument checks (Refining based on diff signatures)
            // setConnection expects 1 argument (Connection)
            if ("setConnection".equals(methodName) && candidate.getArguments().size() != 1) {
                return false;
            }
            // getters/close usually have 0 args
            if (("getProperties".equals(methodName) || "getBasedir".equals(methodName) || "close".equals(methodName))
                && !candidate.getArguments().isEmpty()) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Strategy: Since the 'New' API is not provided in the diff, we cannot automatically 
            // replace the call. We annotating the code to flag the breaking change for the developer.
            
            String methodName = invocation.getExecutable().getSimpleName();
            String msg = String.format("FIXME: Method '%s' was removed or modified in AbstractDatabase superclass. Check migration guide.", methodName);

            // Add an inline suffix comment if possible, or a prefix comment
            invocation.addComment(getFactory().Code().createComment(msg, CtComment.CommentType.BLOCK));
            
            System.out.println("Refactored: Flagged " + methodName + " at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/eb82573b99b6d9688e557d3490fa5d3e9512c99b/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/eb82573b99b6d9688e557d3490fa5d3e9512c99b/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/eb82573b99b6d9688e557d3490fa5d3e9512c99b/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/eb82573b99b6d9688e557d3490fa5d3e9512c99b/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and robust refactoring
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new DatabaseMethodProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}