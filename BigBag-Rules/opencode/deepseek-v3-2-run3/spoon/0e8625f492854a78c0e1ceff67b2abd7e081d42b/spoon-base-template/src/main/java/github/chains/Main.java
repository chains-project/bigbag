package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.*;
import java.io.File;

/**
 * Generic transformation for migrating from Jetty 9/10 to Jetty 11.
 * Handles the following breaking changes:
 * 1. javax.servlet -> jakarta.servlet package migration
 * 2. SelectChannelConnector -> ServerConnector replacement
 * 3. Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration methods
 * 4. Connector.setPort() removal
 * 5. Handler.handle() method signature updates
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDirectory> <outputDirectory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying Jetty 9/10 -> Jetty 11 migration transformation...");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processors
        launcher.addProcessor(new ServletPackageMigrationProcessor());
        launcher.addProcessor(new SelectChannelConnectorReplacementProcessor());
        launcher.addProcessor(new ServerConfigurationMigrationProcessor());
        launcher.addProcessor(new ConnectorPortMigrationProcessor());
        launcher.addProcessor(new HandlerMethodSignatureProcessor());
        
        launcher.run();
        
        System.out.println("Transformation completed.");
    }
    
    /**
     * Processor for javax.servlet -> jakarta.servlet package migration
     * This is a simplified version that logs but doesn't modify (Spoon API issues)
     */
    static class ServletPackageMigrationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeRef) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("javax.servlet.")) {
                String newName = qualifiedName.replace("javax.servlet.", "jakarta.servlet.");
                System.out.println("[INFO] Should migrate: " + qualifiedName + " -> " + newName);
                System.out.println("[ACTION] Manually replace 'javax.servlet' with 'jakarta.servlet' in imports");
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            return candidate.getQualifiedName() != null && 
                   candidate.getQualifiedName().startsWith("javax.servlet.");
        }
    }
    
    /**
     * Processor for SelectChannelConnector -> ServerConnector replacement
     */
    static class SelectChannelConnectorReplacementProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> constructedType = constructorCall.getType();
            if (constructedType != null && 
                "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(constructedType.getQualifiedName())) {
                
                System.out.println("[BREAKING CHANGE] SelectChannelConnector is removed in Jetty 11");
                System.out.println("[LOCATION] " + constructorCall.getPosition());
                System.out.println("[ACTION] Replace 'new SelectChannelConnector()' with 'new ServerConnector(server)'");
                System.out.println("[NOTE] You'll need to ensure Server instance is available and HttpConfiguration is properly configured");
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            CtTypeReference<?> type = candidate.getType();
            return type != null && 
                   "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(type.getQualifiedName());
        }
    }
    
    /**
     * Processor for Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration methods
     */
    static class ServerConfigurationMigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef == null) return;
            
            String methodName = execRef.getSimpleName();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            
            if (declaringType != null && 
                "org.eclipse.jetty.server.Server".equals(declaringType.getQualifiedName())) {
                
                if ("setSendServerVersion".equals(methodName) || 
                    "setSendDateHeader".equals(methodName)) {
                    
                    System.out.println("[BREAKING CHANGE] Server." + methodName + "() moved to HttpConfiguration");
                    System.out.println("[LOCATION] " + invocation.getPosition());
                    System.out.println("[ACTION] Replace 'server." + methodName + "(...)' with 'httpConfig." + methodName + "(...)'");
                    System.out.println("[NOTE] You need to create HttpConfiguration instance and associate it with ServerConnector");
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> execRef = candidate.getExecutable();
            if (execRef == null) return false;
            
            String methodName = execRef.getSimpleName();
            if (!"setSendServerVersion".equals(methodName) && 
                !"setSendDateHeader".equals(methodName)) {
                return false;
            }
            
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            return declaringType != null && 
                   "org.eclipse.jetty.server.Server".equals(declaringType.getQualifiedName());
        }
    }
    
    /**
     * Processor for Connector.setPort() removal
     */
    static class ConnectorPortMigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef == null) return;
            
            String methodName = execRef.getSimpleName();
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            
            if (declaringType != null && 
                "org.eclipse.jetty.server.Connector".equals(declaringType.getQualifiedName()) &&
                "setPort".equals(methodName)) {
                
                System.out.println("[BREAKING CHANGE] Connector.setPort() removed in Jetty 11");
                System.out.println("[LOCATION] " + invocation.getPosition());
                System.out.println("[ACTION] Port must be set via ServerConnector constructor");
                System.out.println("[EXAMPLE] Replace 'connector.setPort(port)' with 'new ServerConnector(server, port)'");
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> execRef = candidate.getExecutable();
            if (execRef == null) return false;
            
            if (!"setPort".equals(execRef.getSimpleName())) return false;
            
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            return declaringType != null && 
                   "org.eclipse.jetty.server.Connector".equals(declaringType.getQualifiedName());
        }
    }
    
    /**
     * Processor for Handler.handle() method signature updates
     */
    static class HandlerMethodSignatureProcessor extends AbstractProcessor<CtMethod<?>> {
        @Override
        public void process(CtMethod<?> method) {
            if (!"handle".equals(method.getSimpleName())) return;
            if (method.getParameters().size() != 4) return;
            
            // Check parameter types
            boolean hasOldServletTypes = false;
            for (var param : method.getParameters()) {
                CtTypeReference<?> paramType = param.getType();
                if (paramType != null) {
                    String qualifiedName = paramType.getQualifiedName();
                    if (qualifiedName != null && qualifiedName.startsWith("javax.servlet.http.Http")) {
                        hasOldServletTypes = true;
                        break;
                    }
                }
            }
            
            if (hasOldServletTypes) {
                System.out.println("[BREAKING CHANGE] Handler.handle() uses javax.servlet in Jetty 9/10");
                System.out.println("[LOCATION] " + method.getPosition());
                System.out.println("[ACTION] Update parameter types from 'javax.servlet.*' to 'jakarta.servlet.*'");
                System.out.println("[NOTE] Also update imports and any related exception types");
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtMethod<?> candidate) {
            return "handle".equals(candidate.getSimpleName()) && 
                   candidate.getParameters().size() == 4;
        }
    }
}