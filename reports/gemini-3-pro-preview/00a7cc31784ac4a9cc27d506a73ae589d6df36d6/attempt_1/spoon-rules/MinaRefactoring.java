package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class MinaRefactoring {

    /**
     * Processor to handle the removal of SslFilter.PEER_ADDRESS.
     * Strategy: Replace the removed constant reference with its String literal value 
     * ("org.apache.mina.filter.ssl.peerAddress") to maintain runtime behavior 
     * while fixing compilation errors, as the field no longer exists in the library.
     */
    public static class SslFilterPeerAddressProcessor extends AbstractProcessor<CtFieldAccess<?>> {

        @Override
        public boolean isToBeProcessed(CtFieldAccess<?> candidate) {
            // 1. Variable Name Check
            // We are looking for the field named "PEER_ADDRESS"
            if (!"PEER_ADDRESS".equals(candidate.getVariable().getSimpleName())) {
                return false;
            }

            // 2. Declaring Type Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            
            // If we can't determine the type, we skip to avoid false positives.
            // We check if the type name contains "SslFilter".
            if (declaringType == null || !declaringType.getQualifiedName().contains("SslFilter")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtFieldAccess<?> fieldAccess) {
            Factory factory = getFactory();

            // The value of org.apache.mina.filter.ssl.SslFilter.PEER_ADDRESS was "org.apache.mina.filter.ssl.peerAddress"
            // We replace the field access with this string literal.
            CtLiteral<String> stringLiteral = factory.Code().createLiteral("org.apache.mina.filter.ssl.peerAddress");

            // Add a comment to explain the change
            stringLiteral.addComment(
                factory.Code().createComment("Refactoring: SslFilter.PEER_ADDRESS was removed. Replaced with literal value.", CtComment.CommentType.INLINE)
            );

            // Perform replacement
            fieldAccess.replace(stringLiteral);
            
            System.out.println("Refactored SslFilter.PEER_ADDRESS at line " + fieldAccess.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (can be modified or passed as args in a real scenario)
        String inputPath = "/Users/frankreyesgarcia/Documents/WORK/PHD/Transformer/output/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/quickfixj/quickfixj-core/src/main/java/quickfix/mina/ssl/SSLFilter.java";
        String outputPath = "reports/gemini-3-pro-preview/00a7cc31784ac4a9cc27d506a73ae589d6df36d6/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory(outputPath);

        // CRITICAL SETTINGS for Spoon 11+ and formatting preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for robust source code preservation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Handle missing dependencies gracefully
        launcher.getEnvironment().setNoClasspath(true);

        // Add the processor
        launcher.addProcessor(new SslFilterPeerAddressProcessor());

        try {
            System.out.println("Starting refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Check output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}