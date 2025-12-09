package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JooqRefactoring {

    /**
     * Processor to identify and flag potential usage of methods removed from
     * org.jooq.meta.postgres.PostgresDatabase.
     * <p>
     * Logic Deduced from Diff:
     * 1. PostgresDatabase has 'METHOD_REMOVED_IN_SUPERCLASS(MAJOR)'.
     * 2. The diff explicitly whitelists 'getProperties', 'getBasedir', 'setConnection', 'close' as UNCHANGED.
     * 3. Strategy: Flag all other method calls on PostgresDatabase as potential compilation errors.
     */
    public static class PostgresDatabaseSafetyProcessor extends AbstractProcessor<CtInvocation<?>> {

        // Methods explicitly marked UNCHANGED in the diff (plus standard Object methods)
        private static final Set<String> SAFE_METHODS = new HashSet<>(Arrays.asList(
            "getProperties",
            "getBasedir",
            "setConnection",
            "close",
            "toString",
            "hashCode",
            "equals",
            "getClass",
            "wait",
            "notify",
            "notifyAll"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check (Optimization)
            String methodName = candidate.getExecutable().getSimpleName();
            if (SAFE_METHODS.contains(methodName)) {
                return false; // These are safe per the diff
            }

            // 2. Target Type Check (Defensive for NoClasspath)
            // We need to verify if the method is being called on PostgresDatabase or AbstractDatabase
            
            // Check the declaring type of the method (Static analysis of the method definition)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                if (typeName.contains("org.jooq.meta.postgres.PostgresDatabase") || 
                    typeName.contains("org.jooq.meta.AbstractDatabase")) {
                    return true;
                }
            }

            // Check the type of the expression the method is called on (e.g., db.someMethod())
            CtExpression<?> target = candidate.getTarget();
            if (target != null) {
                CtTypeReference<?> targetType = target.getType();
                if (targetType != null) {
                    String targetTypeName = targetType.getQualifiedName();
                    // Fuzzy match to handle NoClasspath resolution limitations
                    if (targetTypeName.contains("PostgresDatabase") || targetTypeName.contains("AbstractDatabase")) {
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Refactoring Action: Attach a warning comment.
            // Since the exact removed method name wasn't in the diff, we flag this invocation
            // so the developer can verify if this is the missing method.
            
            String warningMsg = " FIXME: [JOOQ-MIGRATION] Potential breaking change. " +
                                "The method '" + invocation.getExecutable().getSimpleName() + 
                                "' may have been removed in the superclass.";

            // Add an inline comment (/* ... */) before the invocation
            invocation.addComment(
                getFactory().Code().createComment(warningMsg, CtComment.CommentType.BLOCK)
            );

            System.out.println("Flagged potential breaking change at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/3d0cc7db9d9c9420fa4f478a72067ef846ba8129/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3d0cc7db9d9c9420fa4f478a72067ef846ba8129/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/3d0cc7db9d9c9420fa4f478a72067ef846ba8129/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/3d0cc7db9d9c9420fa4f478a72067ef846ba8129/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and Robustness
        // 1. Enable comments to preserve existing code documentation
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive assumption)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new PostgresDatabaseSafetyProcessor());

        try {
            System.out.println("Starting jOOQ Refactoring Scan...");
            launcher.run();
            System.out.println("Scan complete. Check " + outputPath + " for flagged code.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}