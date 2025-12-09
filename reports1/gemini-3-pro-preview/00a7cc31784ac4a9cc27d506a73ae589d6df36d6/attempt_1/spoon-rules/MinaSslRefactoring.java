package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class MinaSslRefactoring {

    /**
     * Processor to handle the removal of SslFilter.initiateHandshake methods.
     * In MINA 2.x updates, the handshake is automated, so explicit calls should be removed.
     */
    public static class SslHandshakeRemovalProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Method Name Check
            if (!"initiateHandshake".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (1 or 2 arguments based on the diff)
            int argCount = candidate.getArguments().size();
            if (argCount != 1 && argCount != 2) {
                return false;
            }

            // 3. Defensive Owner/Target Check
            // We want to ensure we are modifying calls on SslFilter, but we must handle NoClasspath (null types).
            CtExpression<?> target = candidate.getTarget();
            if (target != null) {
                CtTypeReference<?> type = target.getType();
                // If the type is resolved (not null) and does NOT contain "SslFilter", skip it.
                // If it is unknown (null) or matches, we process it.
                if (type != null && !type.getQualifiedName().contains("SslFilter") && !type.getQualifiedName().equals("<unknown>")) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Strategy:
            // The method is removed because handshake is now automatic.
            // 1. If it's a standalone statement in a block, delete it.
            // 2. If it's part of an expression (assignment/condition), replace with 'true' to maintain compilation 
            //    (assuming success) while preserving flow.
            
            if (invocation.getParent() instanceof CtBlock) {
                // Standalone statement: filter.initiateHandshake(session);
                invocation.delete();
                System.out.println("Refactoring: Removed statement SslFilter.initiateHandshake at line " + 
                    (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "?"));
            } else {
                // Expression context: boolean b = filter.initiateHandshake(session);
                // Replace with literal 'true' to simulate "initiated".
                invocation.replace(getFactory().Code().createLiteral(true));
                System.out.println("Refactoring: Replaced SslFilter.initiateHandshake expression with 'true' at line " + 
                    (invocation.getPosition().isValidPosition() ? invocation.getPosition().getLine() : "?"));
            }
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/initiator/InitiatorProxyIoHandler.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/initiator/InitiatorProxyIoHandler.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed");

        // CRITICAL: Configure Environment for accurate source preservation
        // 1. Enable comments to preserve existing documentation
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Use SniperJavaPrettyPrinter to only print modified AST nodes, preserving formatting of others
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode to handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new SslHandshakeRemovalProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}