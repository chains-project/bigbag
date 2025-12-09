package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class JooqPostgresRefactoring {

    /**
     * Processor to handle the removal of a method in the PostgresDatabase superclass hierarchy.
     * Since the input diff did not explicitly name the removed method (but flagged the class as affected),
     * this processor targets a configurable method name.
     */
    public static class RemovedMethodProcessor extends AbstractProcessor<CtInvocation<?>> {

        // TODO: Update this string with the actual name of the removed method from your compilation error log.
        // Common jOOQ metadata removals include methods like 'getSchemata', 'loadPrimaryKeys', etc.
        private static final String REMOVED_METHOD_NAME = "methodNameFromDiff";

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String methodName = candidate.getExecutable().getSimpleName();
            if (!REMOVED_METHOD_NAME.equals(methodName)) {
                return false;
            }

            // 2. Check Owner Type (Defensive for NoClasspath)
            // We check if the method belongs to AbstractDatabase or PostgresDatabase (or subclasses)
            CtExecutableReference<?> executable = candidate.getExecutable();
            CtTypeReference<?> declaringType = executable.getDeclaringType();

            if (declaringType == null) {
                return false; // Cannot determine owner
            }

            String ownerName = declaringType.getQualifiedName();
            // Match loose strings to handle NoClasspath resolution issues
            boolean isJooqMeta = ownerName.contains("org.jooq.meta.AbstractDatabase") 
                              || ownerName.contains("org.jooq.meta.postgres.PostgresDatabase")
                              || ownerName.contains("Database"); // Broad match for custom subclasses

            return isJooqMeta;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Strategy: Comment out the invocation to fix the breaking change while preserving intent.
            // We wrap the invocation in a block comment with a FIXME marker.
            
            CtElement parent = invocation.getParent();
            
            // Create the comment content
            String commentContent = "FIXME: Method '" + REMOVED_METHOD_NAME + "' was removed in jOOQ superclass. Refactor this logic: " + invocation.toString();
            CtComment comment = getFactory().createComment(commentContent, CtComment.CommentType.BLOCK);

            if (invocation.getParent() instanceof CtStatement && !(parent instanceof CtInvocation)) {
                // If it's a standalone statement (e.g., db.removedMethod();), replace the whole statement with the comment.
                invocation.replace(comment);
                System.out.println("Refactored (Commented out) statement at line " + invocation.getPosition().getLine());
            } else {
                // If it's part of an expression (e.g., var x = db.removedMethod();), 
                // we cannot safely delete it. We append a comment to the parent statement instead.
                CtStatement parentStmt = invocation.getParent(CtStatement.class);
                if (parentStmt != null) {
                    parentStmt.addComment(comment);
                    System.out.println("Annotated expression at line " + invocation.getPosition().getLine() + " with FIXME comment.");
                }
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/626631e833256a62137c1e5c35c5c5821ca700d5/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/626631e833256a62137c1e5c35c5c5821ca700d5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/626631e833256a62137c1e5c35c5c5821ca700d5/jooq-meta-postgres-flyway/src/main/java/com/github/sabomichal/jooq/PostgresDDLDatabase.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/626631e833256a62137c1e5c35c5c5821ca700d5/attempt_1/transformed");

        // CRITICAL: Configure Environment for Source Preservation (Sniper)
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Defensive mode for missing libraries
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new RemovedMethodProcessor());

        try {
            System.out.println("Starting jOOQ PostgresDatabase Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}