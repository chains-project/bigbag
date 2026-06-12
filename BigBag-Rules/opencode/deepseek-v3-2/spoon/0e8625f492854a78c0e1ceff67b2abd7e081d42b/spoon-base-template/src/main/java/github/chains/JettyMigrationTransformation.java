package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.AbstractFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtParameter;
import java.util.List;
import java.util.ArrayList;

public class JettyMigrationTransformation {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-transformation.jar github.chains.JettyMigrationTransformation <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jetty 11 migration transformation to: " + sourceDir);
        
        SpoonAPI spoon = new Launcher();
        spoon.addInputResource(sourceDir);
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setAutoImports(true);
        spoon.getEnvironment().setCommentEnabled(true);
        
        CtModel model = spoon.buildModel();
        
        // Apply transformations
        boolean changesMade = false;
        
        changesMade |= transformServletImports(model);
        changesMade |= transformSelectChannelConnector(model);
        changesMade |= transformHandlerMethodSignature(model);
        
        if (changesMade) {
            // Write transformed code
            spoon.setSourceOutputDirectory(sourceDir);
            spoon.prettyprint();
            System.out.println("Transformation complete! Files have been updated.");
            
            // Print summary of changes needed
            System.out.println("\n=== MANUAL CHANGES REQUIRED ===");
            System.out.println("1. Update SelectChannelConnector usage to ServerConnector:");
            System.out.println("   OLD: new SelectChannelConnector()");
            System.out.println("   NEW: new ServerConnector(server, port)");
            System.out.println("   Note: Server instance must be passed to constructor");
            System.out.println("\n2. Update setPort() calls:");
            System.out.println("   OLD: connector.setPort(port)");
            System.out.println("   NEW: Port must be set via ServerConnector constructor");
            System.out.println("\n3. Check javax.servlet -> jakarta.servlet imports");
            System.out.println("   Most imports have been automatically updated");
        } else {
            System.out.println("No Jetty migration issues found in the codebase.");
        }
    }
    
    private static boolean transformServletImports(CtModel model) {
        System.out.println("Checking for javax.servlet imports...");
        boolean changesMade = false;
        
        // Find all type references containing javax.servlet
        List<CtTypeReference<?>> typeRefsToReplace = new ArrayList<>();
        model.getRootPackage().getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                String qualifiedName = typeRef.getQualifiedName();
                return qualifiedName != null && qualifiedName.contains("javax.servlet");
            }
        }).forEach(typeRefsToReplace::add);
        
        // Replace type references
        for (CtTypeReference<?> typeRef : typeRefsToReplace) {
            String oldType = typeRef.getQualifiedName();
            String newType = oldType.replace("javax.servlet", "jakarta.servlet");
            
            System.out.println("  Replacing: " + oldType + " -> " + newType);
            
            CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newType);
            typeRef.replace(newTypeRef);
            changesMade = true;
        }
        
        return changesMade;
    }
    
    private static boolean transformSelectChannelConnector(CtModel model) {
        System.out.println("Checking for SelectChannelConnector usage...");
        boolean changesMade = false;
        
        // Find all type references to SelectChannelConnector
        List<CtTypeReference<?>> typeRefs = new ArrayList<>();
        model.getRootPackage().getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                String qualifiedName = typeRef.getQualifiedName();
                return qualifiedName != null && 
                       qualifiedName.contains("SelectChannelConnector");
            }
        }).forEach(typeRefs::add);
        
        // Replace type references
        for (CtTypeReference<?> typeRef : typeRefs) {
            String oldType = typeRef.getQualifiedName();
            String newType = "org.eclipse.jetty.server.ServerConnector";
            
            System.out.println("  Replacing: " + oldType + " -> " + newType);
            
            CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(newType);
            typeRef.replace(newTypeRef);
            changesMade = true;
        }
        
        // Find constructor calls
        List<CtConstructorCall<?>> constructorCalls = new ArrayList<>();
        model.getRootPackage().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> typeRef = constructorCall.getType();
                return typeRef != null && 
                       typeRef.getQualifiedName().contains("SelectChannelConnector");
            }
        }).forEach(constructorCalls::add);
        
        // Replace constructor calls
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("  Found SelectChannelConnector constructor call at: " + 
                constructorCall.getPosition().toString());
            
            // Create new ServerConnector type reference
            CtTypeReference<?> serverConnectorType = constructorCall.getFactory()
                .createReference("org.eclipse.jetty.server.ServerConnector");
            
            // We can't automatically fix constructor parameters due to API change
            // Just replace the type and let developers fix the constructor arguments
            constructorCall.setType(serverConnectorType);
            changesMade = true;
        }
        
        return changesMade;
    }
    
    private static boolean transformHandlerMethodSignature(CtModel model) {
        System.out.println("Checking AbstractHandler.handle() method signatures...");
        boolean changesMade = false;
        
        // Find all classes extending AbstractHandler
        List<CtClass<?>> handlerClasses = new ArrayList<>();
        model.getRootPackage().getElements(new AbstractFilter<CtClass<?>>() {
            @Override
            public boolean matches(CtClass<?> ctClass) {
                CtTypeReference<?> superClass = ctClass.getSuperclass();
                if (superClass != null) {
                    String superClassName = superClass.getQualifiedName();
                    return superClassName != null && 
                           superClassName.contains("AbstractHandler");
                }
                return false;
            }
        }).forEach(handlerClasses::add);
        
        for (CtClass<?> handlerClass : handlerClasses) {
            System.out.println("  Processing handler class: " + handlerClass.getQualifiedName());
            
            // Find handle() method
            for (CtMethod<?> method : handlerClass.getMethods()) {
                if ("handle".equals(method.getSimpleName())) {
                    System.out.println("    Found handle() method");
                    
                    // Check and fix parameter types
                    List<CtParameter<?>> params = method.getParameters();
                    if (params.size() >= 4) {
                        // Check and fix 3rd parameter (HttpServletRequest)
                        CtParameter<?> param3 = params.get(2);
                        if (param3.getType().getQualifiedName().contains("javax.servlet.http.HttpServletRequest")) {
                            System.out.println("      Fixing HttpServletRequest parameter");
                            CtTypeReference<?> newType = param3.getFactory()
                                .createReference("jakarta.servlet.http.HttpServletRequest");
                            param3.setType(newType);
                            changesMade = true;
                        }
                        
                        // Check and fix 4th parameter (HttpServletResponse)
                        CtParameter<?> param4 = params.get(3);
                        if (param4.getType().getQualifiedName().contains("javax.servlet.http.HttpServletResponse")) {
                            System.out.println("      Fixing HttpServletResponse parameter");
                            CtTypeReference<?> newType = param4.getFactory()
                                .createReference("jakarta.servlet.http.HttpServletResponse");
                            param4.setType(newType);
                            changesMade = true;
                        }
                    }
                }
            }
        }
        
        return changesMade;
    }
}