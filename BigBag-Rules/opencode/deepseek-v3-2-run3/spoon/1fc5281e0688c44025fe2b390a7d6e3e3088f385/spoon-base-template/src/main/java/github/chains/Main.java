package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.ModifierKind;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Generic transformation rule for Jetty 11 breaking API changes.
 * This transformation handles the migration from older Jetty APIs to Jetty 11.x.
 * 
 * Key transformations:
 * 1. SelectChannelConnector -> ServerConnector migration
 * 2. javax.servlet -> jakarta.servlet migration  
 * 3. Removal of deprecated Server methods (setSendServerVersion, setSendDateHeader)
 * 4. Handler method signature updates
 */
public class Main {
    
    private static final String OLD_CONNECTOR_TYPE = "org.eclipse.jetty.server.nio.SelectChannelConnector";
    private static final String NEW_CONNECTOR_TYPE = "org.eclipse.jetty.server.ServerConnector";
    private static final String SERVER_TYPE = "org.eclipse.jetty.server.Server";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transform.jar <sourceDir> <outputDir>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Jetty 11 Breaking API Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Build the model
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Apply transformations
            int transformations = 0;
            transformations += transformSelectChannelConnector(model, factory);
            transformations += transformServletPackage(model, factory);
            transformations += removeDeprecatedServerMethods(model, factory);
            transformations += updateHandlerSignatures(model, factory);
            
            System.out.println("\n=== Transformation Summary ===");
            System.out.println("Total transformations applied: " + transformations);
            
            if (transformations > 0) {
                // Pretty print the transformed code
                launcher.prettyprint();
                System.out.println("Transformed code written to: " + outputDir);
            } else {
                System.out.println("No transformations were needed.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transform SelectChannelConnector to ServerConnector
     * Pattern: new SelectChannelConnector() -> new ServerConnector(server)
     */
    private static int transformSelectChannelConnector(CtModel model, Factory factory) {
        System.out.println("\n[1] Transforming SelectChannelConnector to ServerConnector...");
        int count = 0;
        
        // Find all constructor calls to SelectChannelConnector
        List<CtConstructorCall<?>> constructorCalls = model.getElements(
            new Filter<CtConstructorCall<?>>() {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    if (element.getType() == null || element.getType().getQualifiedName() == null) {
                        return false;
                    }
                    return element.getType().getQualifiedName().equals(OLD_CONNECTOR_TYPE);
                }
            });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("  Found: " + OLD_CONNECTOR_TYPE + " at " + 
                constructorCall.getPosition().getFile().getName() + ":" + 
                constructorCall.getPosition().getLine());
            
            // Create new ServerConnector constructor call
            CtTypeReference<?> serverConnectorType = factory.Type().createReference(NEW_CONNECTOR_TYPE);
            
            // Create a variable reference for 'server' (simplified - assumes server variable exists)
            CtExpression<?> serverVar = factory.Code().createCodeSnippetExpression("server");
            
            // Create new constructor call: new ServerConnector(server)
            CtConstructorCall<?> newConstructor = factory.Code().createConstructorCall(
                serverConnectorType,
                serverVar
            );
            
            // Replace the old constructor call
            constructorCall.replace(newConstructor);
            count++;
            
            System.out.println("  Replaced with: " + NEW_CONNECTOR_TYPE + "(server)");
        }
        
        System.out.println("  Total transformations: " + count);
        return count;
    }
    
    /**
     * Transform javax.servlet imports and type references to jakarta.servlet
     */
    private static int transformServletPackage(CtModel model, Factory factory) {
        System.out.println("\n[2] Transforming javax.servlet -> jakarta.servlet...");
        int count = 0;
        
        // Find all type references in the model
        List<CtTypeReference<?>> allTypeRefs = model.getElements(
            new Filter<CtTypeReference<?>>() {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    return element.getQualifiedName() != null;
                }
            });
        
        for (CtTypeReference<?> typeRef : allTypeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName.startsWith("javax.servlet.")) {
                String newName = qualifiedName.replace("javax.servlet", "jakarta.servlet");
                
                // Don't replace if it's already the new name
                if (!qualifiedName.equals(newName)) {
                    System.out.println("  Replacing type reference: " + qualifiedName + " -> " + newName);
                    
                    // Create new type reference and replace
                    CtTypeReference<?> newTypeRef = factory.Type().createReference(newName);
                    typeRef.replace(newTypeRef);
                    count++;
                }
            }
        }
        
        // Update imports
        List<CtImport> imports = model.getElements(
            new Filter<CtImport>() {
                @Override
                public boolean matches(CtImport element) {
                    return element.getReference() != null && 
                           element.getReference().toString().startsWith("javax.servlet.");
                }
            });
        
        for (CtImport importDecl : imports) {
            String importName = importDecl.getReference().toString();
            String newImportName = importName.replace("javax.servlet", "jakarta.servlet");
            System.out.println("  Updating import: " + importName + " -> " + newImportName);
            
            // Create new import
            CtImport newImport = factory.Core().createImport();
            newImport.setReference(factory.Type().createReference(newImportName));
            importDecl.replace(newImport);
            count++;
        }
        
        System.out.println("  Total transformations: " + count);
        return count;
    }
    
    /**
     * Remove or replace calls to deprecated Server methods
     */
    private static int removeDeprecatedServerMethods(CtModel model, Factory factory) {
        System.out.println("\n[3] Handling deprecated Server methods...");
        int count = 0;
        
        // Find calls to setSendServerVersion and setSendDateHeader
        List<CtInvocation<?>> invocations = model.getElements(
            new Filter<CtInvocation<?>>() {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    if (element.getExecutable() == null || 
                        element.getExecutable().getSimpleName() == null) {
                        return false;
                    }
                    String methodName = element.getExecutable().getSimpleName();
                    return methodName.equals("setSendServerVersion") || 
                           methodName.equals("setSendDateHeader");
                }
            });
        
        for (CtInvocation<?> invocation : invocations) {
            System.out.println("  Found deprecated method call: " + 
                invocation.getExecutable().getSimpleName() + " at " +
                invocation.getPosition().getFile().getName() + ":" + 
                invocation.getPosition().getLine());
            
            // Replace invocation with a comment
            CtStatement parentStatement = invocation.getParent(CtStatement.class);
            if (parentStatement != null) {
                String comment = "// " + invocation.getExecutable().getSimpleName() + 
                               "() is removed in Jetty 11. Consider alternative configuration.";
                CtCodeSnippetStatement commentStmt = factory.Code().createCodeSnippetStatement(comment);
                parentStatement.replace(commentStmt);
                count++;
                System.out.println("  Replaced with comment");
            }
        }
        
        System.out.println("  Total transformations: " + count);
        return count;
    }
    
    /**
     * Update AbstractHandler.handle() method signatures and implementations
     */
    private static int updateHandlerSignatures(CtModel model, Factory factory) {
        System.out.println("\n[4] Updating Handler method signatures...");
        int count = 0;
        
        // Find classes extending AbstractHandler
        List<CtClass<?>> handlerClasses = model.getElements(
            new Filter<CtClass<?>>() {
                @Override
                public boolean matches(CtClass<?> element) {
                    if (element.getSuperclass() != null) {
                        String superName = element.getSuperclass().getQualifiedName();
                        return superName != null && 
                               superName.equals("org.eclipse.jetty.server.handler.AbstractHandler");
                    }
                    return false;
                }
            });
        
        for (CtClass<?> handlerClass : handlerClasses) {
            System.out.println("  Processing handler class: " + handlerClass.getQualifiedName());
            
            // Find all methods in the class
            for (CtMethod<?> method : handlerClass.getMethods()) {
                // Check if method has parameters with javax.servlet types
                for (CtParameter<?> param : method.getParameters()) {
                    String paramType = param.getType().getQualifiedName();
                    
                    if (paramType != null && paramType.startsWith("javax.servlet.")) {
                        String newType = paramType.replace("javax.servlet", "jakarta.servlet");
                        System.out.println("    Updating parameter in " + method.getSimpleName() + 
                                         ": " + paramType + " -> " + newType);
                        
                        CtTypeReference<?> newTypeRef = factory.Type().createReference(newType);
                        param.setType(newTypeRef);
                        count++;
                    }
                }
            }
        }
        
        System.out.println("  Total transformations: " + count);
        return count;
    }
}