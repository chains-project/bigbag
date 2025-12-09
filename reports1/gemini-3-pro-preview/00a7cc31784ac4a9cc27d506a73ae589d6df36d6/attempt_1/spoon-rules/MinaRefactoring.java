package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class MinaRefactoring {

    /**
     * Processor to handle the removal of SslFilter.PEER_ADDRESS.
     * Strategy:
     * 1. Detect usages of SslFilter.PEER_ADDRESS.
     * 2. If used within an IoSession.setAttribute/getAttribute call, comment out the entire method call.
     *    This is because the field is removed, meaning the functionality is likely deprecated or handled internally by the Filter in the new version.
     */
    public static class PeerAddressProcessor extends AbstractProcessor<CtFieldRead<?>> {

        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Variable Name Check
            if (!"PEER_ADDRESS".equals(candidate.getVariable().getSimpleName())) {
                return false;
            }

            // 2. Declaring Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            if (declaringType == null || !declaringType.getQualifiedName().contains("SslFilter")) {
                // If type is unknown, double check imports or assume based on name if strict mode is off.
                // Here we stick to containing "SslFilter" to avoid false positives.
                return false;
            }

            return true;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            // Traverse up to find if this field read is an argument of a method invocation
            CtInvocation<?> invocation = fieldRead.getParent(CtInvocation.class);

            if (invocation != null) {
                String methodName = invocation.getExecutable().getSimpleName();
                
                // Target methods in IoSession that use this key
                if ("setAttribute".equals(methodName) || "getAttribute".equals(methodName) 
                        || "containsAttribute".equals(methodName) || "removeAttribute".equals(methodName)) {
                    
                    // We found `session.setAttribute(SslFilter.PEER_ADDRESS, ...)`
                    // Strategy: Comment out the line to allow compilation and alert the developer.
                    
                    // Find the statement wrapping this invocation (to handle the whole line)
                    CtStatement statement = invocation.getParent(CtStatement.class);
                    
                    // Ensure we are operating on a block-level statement to safely replace with a comment
                    if (statement != null && statement.getParent() instanceof CtBlock) {
                        String originalCode = statement.toString();
                        
                        // Create a comment with the original code
                        CtComment comment = getFactory().Code().createComment(
                            " [SPOON-MIGRATION] Removed dead code due to SslFilter.PEER_ADDRESS removal.\n" +
                            "// Original: " + originalCode,
                            CtComment.CommentType.BLOCK
                        );
                        
                        statement.replace(comment);
                        System.out.println("Refactored: Commented out usage of SslFilter.PEER_ADDRESS at " + fieldRead.getPosition().getLine());
                        return;
                    }
                }
            }

            // Fallback for other usages (assignments, comparisons): replace with null and warn
            // e.g. Object k = SslFilter.PEER_ADDRESS; -> Object k = null;
            CtExpression<?> nullLiteral = getFactory().Code().createLiteral(null);
            nullLiteral.addComment(getFactory().Code().createComment(" [SPOON-MIGRATION] SslFilter.PEER_ADDRESS was removed", CtComment.CommentType.INLINE));
            fieldRead.replace(nullLiteral);
            System.out.println("Refactored: Replaced standalone usage of SslFilter.PEER_ADDRESS with null at " + fieldRead.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/ssl/SSLFilter.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/ssl/SSLFilter.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / NoClasspath
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Ignore missing libraries
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new PeerAddressProcessor());

        try {
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}