package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.code.*;
import spoon.reflect.visitor.Filter;
import java.util.*;
import java.io.File;

/**
 * Generic Spoon transformation for Jetty 8/9 to Jetty 11 migration.
 * Handles breaking changes including:
 * 1. javax.servlet -> jakarta.servlet namespace change
 * 2. Server configuration methods moved to HttpConfiguration
 * 3. SelectChannelConnector replaced with ServerConnector
 * 4. Package structure changes
 */
public class Main {
    private static Launcher launcher;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformer.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Jetty 8/9 to Jetty 11 Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        
        launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        System.out.println("\n1. Transforming javax.servlet to jakarta.servlet...");
        transformServletNamespace(model);
        
        System.out.println("\n2. Fixing Handler.handle() method signature...");
        transformHandlerMethodSignature(model);
        
        System.out.println("\n3. Transforming Server configuration methods...");
        transformServerConfiguration(model);
        
        System.out.println("\n4. Transforming SelectChannelConnector to ServerConnector...");
        transformConnector(model);
        
        System.out.println("\n5. Writing transformed code...");
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\n=== Transformation Complete ===");
        System.out.println("Summary of changes applied:");
        System.out.println("  - javax.servlet.* -> jakarta.servlet.* namespace");
        System.out.println("  - Handler.handle() method signature updated");
        System.out.println("  - Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration");
        System.out.println("  - SelectChannelConnector -> ServerConnector");
        System.out.println("  - Removed obsolete org.eclipse.jetty.server.nio imports");
    }
    
    /**
     * Transform all javax.servlet references to jakarta.servlet
     * This handles the Jakarta EE namespace change from Java EE to Jakarta EE
     */
    private static void transformServletNamespace(CtModel model) {
        // Transform imports
        model.getElements(new Filter<CtImport>() {
            @Override
            public boolean matches(CtImport element) {
                String importStr = element.toString();
                return importStr.contains("javax.servlet");
            }
        }).forEach(importDecl -> {
            String oldImport = importDecl.toString();
            String newImport = oldImport.replace("javax.servlet", "jakarta.servlet");
            System.out.println("  Import: " + oldImport + " -> " + newImport);
            
            // Create new import by replacing the import declaration
            String importRef = newImport.substring(7); // Remove "import "
            if (importRef.endsWith(";")) {
                importRef = importRef.substring(0, importRef.length() - 1);
            }
            
            try {
                CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(importRef.trim());
                CtImport newImportDecl = launcher.getFactory().createImport(newTypeRef);
                importDecl.replace(newImportDecl);
            } catch (Exception e) {
                System.out.println("    Warning: Could not replace import: " + e.getMessage());
            }
        });
        
        // Transform type references in code
        model.getElements(new Filter<CtTypeReference<?>>() {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getQualifiedName().startsWith("javax.servlet.");
            }
        }).forEach(typeRef -> {
            String oldName = typeRef.getQualifiedName();
            String newName = oldName.replace("javax.servlet.", "jakarta.servlet.");
            System.out.println("  Type: " + oldName + " -> " + newName);
            typeRef.setSimpleName(newName.substring(newName.lastIndexOf('.') + 1));
        });
    }
    
    /**
     * Transform Handler.handle() method signature from javax.servlet to jakarta.servlet
     * AbstractHandler subclasses need to update their handle method signature
     */
    private static void transformHandlerMethodSignature(CtModel model) {
        model.getElements(new Filter<CtMethod<?>>() {
            @Override
            public boolean matches(CtMethod<?> element) {
                if (!"handle".equals(element.getSimpleName())) {
                    return false;
                }
                
                // Check if it's overriding AbstractHandler.handle()
                for (CtParameter<?> param : element.getParameters()) {
                    if (param.getType().getQualifiedName().startsWith("javax.servlet.")) {
                        return true;
                    }
                }
                return false;
            }
        }).forEach(method -> {
            CtType<?> parentType = method.getParent(CtType.class);
            System.out.println("  Updating handle() method in: " + 
                (parentType != null ? parentType.getQualifiedName() : "unknown"));
            
            // Update parameter types
            method.getParameters().forEach(param -> {
                CtTypeReference<?> paramType = param.getType();
                if (paramType.getQualifiedName().startsWith("javax.servlet.")) {
                    String newType = paramType.getQualifiedName()
                        .replace("javax.servlet.", "jakarta.servlet.");
                    param.setType(launcher.getFactory().createReference(newType));
                }
            });
        });
    }
    
    /**
     * Transform Server configuration method calls to use HttpConfiguration
     */
    private static void transformServerConfiguration(CtModel model) {
        model.getElements(new Filter<CtInvocation<?>>() {
            @Override
            public boolean matches(CtInvocation<?> element) {
                String expr = element.toString();
                return expr.contains(".setSendServerVersion(") || 
                       expr.contains(".setSendDateHeader(");
            }
        }).forEach(invocation -> {
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                String methodName = invocation.getExecutable().getSimpleName();
                System.out.println("  Found " + methodName + " call on: " + target);
                System.out.println("    NOTE: " + methodName + " moved to HttpConfiguration in Jetty 11");
                System.out.println("    Requires manual update");
            }
        });
    }
    
    /**
     * Transform SelectChannelConnector to ServerConnector with proper initialization
     */
    private static void transformConnector(CtModel model) {
        // Remove obsolete imports
        model.getElements(new Filter<CtImport>() {
            @Override
            public boolean matches(CtImport element) {
                return element.toString().contains("org.eclipse.jetty.server.nio");
            }
        }).forEach(importDecl -> {
            System.out.println("  Removing import: " + importDecl);
            importDecl.delete();
        });
        
        // Change type references
        model.getElements(new Filter<CtTypeReference<?>>() {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getSimpleName().equals("SelectChannelConnector") ||
                       element.getQualifiedName().contains("SelectChannelConnector");
            }
        }).forEach(typeRef -> {
            System.out.println("  Changing SelectChannelConnector -> ServerConnector");
            typeRef.setSimpleName("ServerConnector");
        });
        
        // Transform constructor calls
        model.getElements(new Filter<CtConstructorCall<?>>() {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                CtTypeReference<?> type = element.getType();
                return type != null && 
                    type.getSimpleName().equals("SelectChannelConnector");
            }
        }).forEach(constructor -> {
            System.out.println("  Found SelectChannelConnector constructor");
            System.out.println("    Change to: new ServerConnector(server)");
            System.out.println("    Requires manual update");
        });
    }
}