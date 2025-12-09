package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JettyRefactoring {

    /**
     * Processor 1: Handle Class Migration
     * Detects usage of the removed `SelectChannelConnector` (NIO) and migrates it to `ServerConnector` (Jetty 9+).
     * This handles imports, variable declarations, and constructor calls (new SelectChannelConnector()).
     */
    public static class ConnectorTypeProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Defensive: ensure qualified name is available
            if (candidate.getQualifiedName() == null) return false;
            
            // Match the specific removed class
            return candidate.getQualifiedName().equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
        }

        @Override
        public void process(CtTypeReference<?> candidate) {
            // Logic: Migrate package and class name.
            // Old: org.eclipse.jetty.server.nio.SelectChannelConnector
            // New: org.eclipse.jetty.server.ServerConnector
            
            candidate.setPackage(getFactory().Package().getOrCreate("org.eclipse.jetty.server"));
            candidate.setSimpleName("ServerConnector");
            
            System.out.println("Refactored SelectChannelConnector to ServerConnector at line " 
                + (candidate.getPosition().isValidPosition() ? candidate.getPosition().getLine() : "unknown"));
        }
    }

    /**
     * Processor 2: Handle Removed Server Configuration Methods
     * `setSendServerVersion` and `setSendDateHeader` were removed from `Server` and moved to `HttpConfiguration`.
     * Since this requires structural changes (creating a config object), we comment out the code with a FIXME.
     */
    public static class ServerMethodProcessor extends AbstractProcessor<CtInvocation<?>> {
        private static final Set<String> MOVED_METHODS = new HashSet<>(Arrays.asList(
            "setSendServerVersion", 
            "setSendDateHeader"
        ));

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            String methodName = candidate.getExecutable().getSimpleName();
            if (!MOVED_METHODS.contains(methodName)) return false;

            // 2. Owner Check (Defensive for NoClasspath)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String qName = declaringType.getQualifiedName();
                // We process if it belongs to 'Server' or if type is unknown (NoClasspath inference)
                // We skip if we are certain it is some other class.
                if (!qName.contains("Server") && !qName.equals("<unknown>")) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            String method = invocation.getExecutable().getSimpleName();
            
            // Transformation: Replace the call with a block comment containing the original code and a TODO.
            String originalCode = invocation.toString();
            String commentText = String.format(" FIXME: Method '%s' moved to HttpConfiguration. Original: %s", 
                method, originalCode);
            
            CtComment comment = getFactory().Code().createComment(commentText, CtComment.CommentType.BLOCK);

            try {
                invocation.replace(comment);
                System.out.println("Commented out removed Server method '" + method + "' at line " 
                    + invocation.getPosition().getLine());
            } catch (Exception e) {
                // Fallback if replacement fails (e.g. inside a complex expression)
                System.err.println("Could not replace invocation at line " + invocation.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable)
        String inputPath = "/home/kth/Documents/last_transformer/output/95b2c15de16fd9fd612ce73672e29b613ce7a909/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/95b2c15de16fd9fd612ce73672e29b613ce7a909/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/95b2c15de16fd9fd612ce73672e29b613ce7a909/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/95b2c15de16fd9fd612ce73672e29b613ce7a909/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for precise modification
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode to handle partial dependencies
        launcher.getEnvironment().setNoClasspath(true);

        // Register Processors
        launcher.addProcessor(new ConnectorTypeProcessor());
        launcher.addProcessor(new ServerMethodProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}