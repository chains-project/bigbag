package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class SslFilterRefactoring {

    /**
     * Processor to handle the removal of org.apache.mina.filter.ssl.SslFilter.setUseClientMode(boolean).
     * Strategy: Since the method is removed and configuration likely moved to SSLContext initialization
     * or is no longer required in the same way, we comment out the code to prevent compilation errors
     * and add a FIXME for manual review.
     */
    public static class SslFilterProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"setUseClientMode".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (it took 1 boolean)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Owner/Target Check (Defensive for NoClasspath)
            // We check if the method belongs to SslFilter.
            CtExpression<?> target = candidate.getTarget();
            if (target != null) {
                CtTypeReference<?> type = target.getType();
                // If type is known and does not contain "SslFilter", skip it.
                // If type is null or "<unknown>", we process it to be safe in NoClasspath mode.
                if (type != null && !type.getQualifiedName().contains("SslFilter") && !type.getQualifiedName().equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Capture the original code to preserve it in the comment
            String originalCode = invocation.toString();

            // Create a FIXME block comment explaining the removal
            String message = " FIXME: The method 'setUseClientMode(boolean)' was removed in this version of Apache MINA.\n" +
                             " * This configuration should likely be handled within the SSLContext or SSLEngine creation logic.\n" +
                             " * Original code: " + originalCode;

            CtComment comment = factory.Code().createComment(message, CtComment.CommentType.BLOCK);

            // Replacement Strategy:
            // Since this is a void setter, it is typically used as a Statement.
            // We replace the statement with the comment.
            if (invocation.getParent() instanceof CtStatement) {
                // If the invocation is wrapped (e.g., Implicit Block), replace the parent statement
                CtStatement parentStatement = (CtStatement) invocation.getParent();
                
                // Ensure we don't delete the whole block if it's just the invocation inside a block
                if (parentStatement instanceof CtBlock) {
                    invocation.replace(comment);
                } else {
                    parentStatement.replace(comment);
                }
            } else {
                // Fallback for direct replacement
                invocation.replace(comment);
            }

            System.out.println("Refactored setUseClientMode usage at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/initiator/IoSessionInitiator.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/initiator/IoSessionInitiator.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed");

        // CRITICAL SETTINGS for robust transformation
        // 1. Enable comments to preserve existing code structure
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force Sniper Printer manually for high-fidelity source preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode to run without full dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new SslFilterProcessor());

        try {
            System.out.println("Starting SslFilter Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}