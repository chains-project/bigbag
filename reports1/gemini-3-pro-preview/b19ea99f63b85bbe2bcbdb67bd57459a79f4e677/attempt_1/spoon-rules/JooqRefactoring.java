package org.example.jooq.migration;

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
     * Processor to audit 'PostgresDatabase' usages.
     * Since the diff indicates a METHOD_REMOVED_IN_SUPERCLASS but only lists UNCHANGED methods,
     * we flag any invocation that is NOT in the safe list.
     */
    public static class PostgresDatabaseAuditProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        // Whitelist of methods explicitly marked UNCHANGED in the diff
        private static final Set<String> SAFE_METHODS = new HashSet<>(Arrays.asList(
            "getProperties", 
            "getBasedir", 
            "setConnection", 
            "close"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check target existence
            CtExpression<?> target = candidate.getTarget();
            if (target == null) {
                return false;
            }

            // 2. Type Check (Defensive for NoClasspath)
            CtTypeReference<?> type = target.getType();
            if (type == null) {
                // If we can't resolve the type, we skip to avoid false positives
                return false; 
            }

            // 3. Match specific class 'PostgresDatabase'
            // Using loose matching for NoClasspath robustness
            if (!type.getQualifiedName().contains("org.jooq.meta.postgres.PostgresDatabase") && 
                !type.getQualifiedName().endsWith("PostgresDatabase")) {
                return false;
            }

            // 4. Method Name Check
            // If the method is in the "Safe List" (Unchanged), we don't need to process it.
            String methodName = candidate.getExecutable().getSimpleName();
            if (SAFE_METHODS.contains(methodName)) {
                return false;
            }

            // 5. Avoid double tagging (idempotency)
            for (CtComment comment : candidate.getComments()) {
                if (comment.getContent().contains("METHOD_REMOVED_IN_SUPERCLASS")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Transformation: Add a warning comment to the potentially broken method call
            String methodName = invocation.getExecutable().getSimpleName();
            String msg = String.format(
                " TODO: Verify '%s' exists. Diff indicates METHOD_REMOVED_IN_SUPERCLASS for PostgresDatabase. ", 
                methodName
            );

            // Create and attach comment
            CtComment comment = getFactory().Code().createComment(msg, CtComment.CommentType.BLOCK);
            invocation.addComment(comment);
            
            System.out.println("Flagged potential breaking change at " + 
                invocation.getPosition().getFile().getName() + ":" + 
                invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/b19ea99f63b85bbe2bcbdb67bd57459a79f4e677/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b19ea99f63b85bbe2bcbdb67bd57459a79f4e677/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/b19ea99f63b85bbe2bcbdb67bd57459a79f4e677/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/b19ea99f63b85bbe2bcbdb67bd57459a79f4e677/attempt_1/transformed");

        // ==========================================================
        // CRITICAL: Robust Sniper Configuration for Source Preservation
        // ==========================================================
        // 1. Enable comment processing
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Manually force SniperJavaPrettyPrinter
        // This ensures existing formatting/indentation is kept exactly as-is
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode (defensive handling of missing dependencies)
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new PostgresDatabaseAuditProcessor());

        // Run the refactoring
        try {
            System.out.println("Starting jOOQ PostgresDatabase Audit...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}