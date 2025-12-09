package org.eclipse.jetty.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class Jetty9Refactoring {

    /**
     * PROCESSOR 1: SelectChannelConnector Migration
     * Renames 'SelectChannelConnector' (NIO) to 'ServerConnector' (Jetty 9 standard).
     * Also detects usage of the default constructor (which was removed) and adds a warning.
     */
    public static class ConnectorMigrationProcessor extends AbstractProcessor<CtElement> {
        @Override
        public boolean isToBeProcessed(CtElement candidate) {
            // Process TypeReferences for renaming
            if (candidate instanceof CtTypeReference) {
                return "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(((CtTypeReference<?>) candidate).getQualifiedName());
            }
            // Process Constructor Calls to add semantic warnings
            if (candidate instanceof CtConstructorCall) {
                CtTypeReference<?> type = ((CtConstructorCall<?>) candidate).getType();
                return type != null && "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(type.getQualifiedName());
            }
            return false;
        }

        @Override
        public void process(CtElement candidate) {
            Factory factory = getFactory();

            if (candidate instanceof CtTypeReference) {
                CtTypeReference<?> ref = (CtTypeReference<?>) candidate;
                // Rename type to ServerConnector
                ref.setPackage(factory.Package().getOrCreate("org.eclipse.jetty.server"));
                ref.setSimpleName("ServerConnector");
            } 
            else if (candidate instanceof CtConstructorCall) {
                CtConstructorCall<?> ctor = (CtConstructorCall<?>) candidate;
                // SelectChannelConnector had a no-arg constructor. ServerConnector usually requires a Server instance.
                // We add a comment instructing the user to fix arguments.
                if (ctor.getArguments().isEmpty()) {
                    // We append a comment to the statement containing this constructor
                    CtStatement parent = ctor.getParent(CtStatement.class);
                    if (parent != null) {
                        parent.addComment(factory.Code().createComment("FIXME: ServerConnector requires a Server instance in constructor", CtComment.CommentType.INLINE));
                    }
                }
            }
        }
    }

    /**
     * PROCESSOR 2: Server Configuration Migration
     * Handles methods removed from 'Server' class and moved to 'HttpConfiguration'.
     * - setSendServerVersion(boolean)
     * - setSendDateHeader(boolean)
     */
    public static class ServerConfigProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Check Method Name
            String name = candidate.getExecutable().getSimpleName();
            if (!"setSendServerVersion".equals(name) && !"setSendDateHeader".equals(name)) {
                return false;
            }

            // 2. Check Owner Type (Defensive)
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) return false;
            
            // Matches "org.eclipse.jetty.server.Server" or simple "Server"
            return declaringType.getQualifiedName().contains("Server");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Action: Comment out the line and add a migration TODO
            // We cannot easily automate this as it requires instantiating HttpConfiguration and wiring it to a Connector.
            
            String methodName = invocation.getExecutable().getSimpleName();
            String originalStatement = invocation.toString();
            
            String todoText = String.format(
                "TODO: %s removed from Server. Move this config to HttpConfiguration.\nOriginal: %s", 
                methodName, originalStatement
            );

            CtComment comment = getFactory().Code().createComment(todoText, CtComment.CommentType.BLOCK);
            
            CtStatement parentStmt = invocation.getParent(CtStatement.class);
            if (parentStmt != null) {
                parentStmt.replace(comment);
                System.out.println("Deprecated Server method processed at line " + invocation.getPosition().getLine());
            }
        }
    }

    /**
     * PROCESSOR 3: Connector.setPort Migration
     * 'setPort' was removed from the 'Connector' interface and 'AbstractConnector'.
     * It now resides in 'AbstractNetworkConnector' / 'ServerConnector'.
     * Usage on a raw 'Connector' type will fail compilation.
     */
    public static class ConnectorSetPortProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            if (!"setPort".equals(candidate.getExecutable().getSimpleName())) return false;

            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            if (owner == null) return false;

            String ownerName = owner.getQualifiedName();
            // If the type is strictly Connector or AbstractConnector, it's a problem.
            // If it's ServerConnector, it's fine.
            return (ownerName.endsWith(".Connector") || ownerName.endsWith(".AbstractConnector"))
                    && !ownerName.contains("ServerConnector") 
                    && !ownerName.contains("NetworkConnector");
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // We add a comment warning the user that casting might be required.
            CtStatement stmt = invocation.getParent(CtStatement.class);
            if (stmt != null) {
                stmt.addComment(getFactory().Code().createComment("FIXME: setPort removed from Connector interface. Cast to ServerConnector?", CtComment.CommentType.INLINE));
            }
        }
    }

    public static void main(String[] args) {
        String inputPath = "/home/kth/Documents/last_transformer/output/f26cd85b97b24c07a2e446f43ac8793619fa0724/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f26cd85b97b24c07a2e446f43ac8793619fa0724/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/f26cd85b97b24c07a2e446f43ac8793619fa0724/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/f26cd85b97b24c07a2e446f43ac8793619fa0724/attempt_1/transformed");

        // CRITICAL: Configure Sniper Printer for preservation
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // Defensive: NoClasspath mode
        launcher.getEnvironment().setNoClasspath(true);

        // Register Processors
        launcher.addProcessor(new ConnectorMigrationProcessor());
        launcher.addProcessor(new ServerConfigProcessor());
        launcher.addProcessor(new ConnectorSetPortProcessor());

        System.out.println("Starting Jetty 9 Migration...");
        try {
            launcher.run();
            System.out.println("Migration finished. Check " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}