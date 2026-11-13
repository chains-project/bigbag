package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic Spoon transformation to fix the breaking change from SelectChannelConnector to ServerConnector
 * in Jetty 11.0.10+.
 * 
 * This transformation:
 * 1. Replaces imports of org.eclipse.jetty.server.nio.SelectChannelConnector with org.eclipse.jetty.server.ServerConnector
 * 2. Replaces "new SelectChannelConnector()" constructor calls with "new ServerConnector(server)"
 * 3. Updates field type from Connector to ServerConnector to access specific methods
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        // Target only the specific file that contains the breaking change
        launcher.addInputResource(sourceDirectory + "/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the model
        launcher.buildModel();
        
        // Apply the transformation
        transformSelectChannelConnectorToServerConnector(launcher);
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(sourceDirectory + "/transformed");
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Transforms SelectChannelConnector usage to ServerConnector usage
     */
    private static void transformSelectChannelConnectorToServerConnector(Launcher launcher) {
        // Find all constructor calls to SelectChannelConnector
        List<CtConstructorCall> constructorCalls = launcher.getModel()
                .getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall constructorCall : constructorCalls) {
            CtTypeReference constructorType = constructorCall.getType();
            
            // Check if this is a SelectChannelConnector constructor call
            if (constructorType != null && 
                "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(constructorType.getQualifiedName())) {
                
                // Get the enclosing class to find the Server field
                CtType<?> enclosingType = constructorCall.getParent(CtType.class);
                if (enclosingType instanceof CtClass) {
                    CtClass<?> enclosingClass = (CtClass<?>) enclosingType;
                    
                    // Find the Server field in the class
                    CtField<?> serverField = findServerField(enclosingClass);
                    
                    if (serverField != null) {
                        // Replace with new ServerConnector(server)
                        replaceSelectChannelConnectorWithServerConnector(constructorCall, serverField);
                        
                        // Update field type from Connector to ServerConnector
                        updateFieldType(enclosingClass);
                        
                        // Update imports
                        updateImports(launcher);
                    } else {
                        System.err.println("Warning: Could not find Server field in class " + enclosingClass.getQualifiedName());
                    }
                }
            }
        }
    }
    
    /**
     * Finds the Server field in the class
     */
    private static CtField<?> findServerField(CtClass<?> enclosingClass) {
        for (CtField<?> field : enclosingClass.getFields()) {
            CtTypeReference fieldType = field.getType();
            if (fieldType != null && 
                "org.eclipse.jetty.server.Server".equals(fieldType.getQualifiedName())) {
                return field;
            }
        }
        return null;
    }
    
    /**
     * Replaces SelectChannelConnector constructor call with ServerConnector constructor call
     */
    private static void replaceSelectChannelConnectorWithServerConnector(
            CtConstructorCall oldConstructorCall, CtField<?> serverField) {
        
        // Create the new constructor call: new ServerConnector(server)
        CtConstructorCall newConstructorCall = oldConstructorCall.getFactory()
                .createConstructorCall(
                    oldConstructorCall.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector")
                );
        
        // Add the server field as parameter
        newConstructorCall.addArgument(
            oldConstructorCall.getFactory().createVariableRead(serverField.getReference(), false)
        );
        
        // Replace the old constructor call with the new one
        oldConstructorCall.replace(newConstructorCall);
        
        System.out.println("Transformed SelectChannelConnector to ServerConnector in " + 
                          oldConstructorCall.getPosition().getCompilationUnit().getFile().getName());
    }
    
    /**
     * Updates field type from Connector to ServerConnector
     */
    private static void updateFieldType(CtClass<?> enclosingClass) {
        for (CtField<?> field : enclosingClass.getFields()) {
            if ("httpConnector".equals(field.getSimpleName())) {
                field.setType(enclosingClass.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector"));
                System.out.println("Updated httpConnector field type to ServerConnector");
            }
        }
    }
    
    /**
     * Updates imports for Jetty 11.0.10
     */
    private static void updateImports(Launcher launcher) {
        // Get all compilation units from the model
        List<CtCompilationUnit> compilationUnits = launcher.getModel().getElements(new TypeFilter<>(CtCompilationUnit.class));
        
        for (CtCompilationUnit compilationUnit : compilationUnits) {
            // Remove old imports
            List<CtImport> importsToRemove = new ArrayList<>();
            for (CtImport importDecl : compilationUnit.getImports()) {
                String importString = importDecl.toString();
                if (importString.contains("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                    importsToRemove.add(importDecl);
                    System.out.println("Removing old import: " + importString);
                }
            }
            compilationUnit.getImports().removeAll(importsToRemove);
            
            // Add new imports if ServerConnector is used
            boolean hasServerConnectorUsage = compilationUnit.getElements(new TypeFilter<>(CtConstructorCall.class))
                .stream()
                .anyMatch(constructorCall -> {
                    CtTypeReference type = constructorCall.getType();
                    return type != null && "org.eclipse.jetty.server.ServerConnector".equals(type.getQualifiedName());
                });
            
            if (hasServerConnectorUsage) {
                // Check if ServerConnector import already exists
                boolean hasServerConnectorImport = compilationUnit.getImports().stream()
                    .anyMatch(importDecl -> importDecl.toString().contains("org.eclipse.jetty.server.ServerConnector"));
                
                if (!hasServerConnectorImport) {
                    System.out.println("Adding new ServerConnector import");
                    // Add import using the factory
                    launcher.getFactory().createImport(
                        launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector")
                    ).setParent(compilationUnit);
                }
            }
        }
    }
}