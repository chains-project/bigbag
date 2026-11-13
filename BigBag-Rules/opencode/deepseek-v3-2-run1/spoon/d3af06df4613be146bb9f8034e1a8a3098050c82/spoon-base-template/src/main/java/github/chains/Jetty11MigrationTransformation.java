package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;

import java.util.List;

/**
 * Generic transformation to migrate from Jetty 9/10 to Jetty 11+
 * Handles the breaking changes in jetty-server 11.0.8
 */
public class Jetty11MigrationTransformation {
    
    private final Launcher launcher;
    private int changeCount = 0;
    
    public Jetty11MigrationTransformation(Launcher launcher) {
        this.launcher = launcher;
    }
    
    public int apply(CtModel model) {
        System.out.println("Applying Jetty 11 migration transformations...");
        
        // 1. Replace SelectChannelConnector with ServerConnector
        replaceSelectChannelConnector(model);
        
        // 2. Update javax.servlet to jakarta.servlet imports
        updateServletImports(model);
        
        // 3. Fix AbstractHandler.handle method signature
        fixAbstractHandlerSubclasses(model);
        
        // 4. Update method calls that changed signature
        updateMethodCalls(model);
        
        System.out.println("Applied " + changeCount + " transformations.");
        return changeCount;
    }
    
    private void replaceSelectChannelConnector(CtModel model) {
        System.out.println("1. Replacing SelectChannelConnector with ServerConnector...");
        
        // Update type references
        List<CtTypeReference> typeRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference typeRef) {
                String qname = typeRef.getQualifiedName();
                return qname != null && qname.equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
            }
        });
        
        for (CtTypeReference typeRef : typeRefs) {
            System.out.println("  Updating type reference: " + typeRef.getQualifiedName());
            typeRef.setQualifiedName("org.eclipse.jetty.server.ServerConnector");
            changeCount++;
        }
        
        // Find and update constructor calls
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall constructorCall) {
                CtTypeReference typeRef = constructorCall.getType();
                return typeRef != null && 
                    "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(typeRef.getQualifiedName());
            }
        });
        
        for (CtConstructorCall constructorCall : constructorCalls) {
            System.out.println("  Replacing constructor at: " + 
                constructorCall.getPosition().getCompilationUnit().getMainType().getQualifiedName() + 
                ":" + constructorCall.getPosition().getLine());
            
            // Create new ServerConnector constructor call
            // Need to find the Server instance in context
            CtExpression serverExpr = findServerInstanceInContext(constructorCall);
            
            if (serverExpr == null) {
                System.err.println("    ERROR: Could not find Server instance for ServerConnector constructor");
                System.err.println("    Manual fix required: new ServerConnector(server)");
                continue;
            }
            
            CtConstructorCall newConstructor = launcher.getFactory().Code()
                .createConstructorCall(
                    launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector"),
                    serverExpr
                );
            
            constructorCall.replace(newConstructor);
            changeCount++;
        }
    }
    
    private CtExpression findServerInstanceInContext(CtConstructorCall constructorCall) {
        // Look for a Server variable declaration in the surrounding code
        CtElement parent = constructorCall.getParent();
        
        // First, check if we're in a constructor or method that has a Server parameter
        while (parent != null) {
            if (parent instanceof CtMethod) {
                CtMethod method = (CtMethod) parent;
                for (CtParameter param : method.getParameters()) {
                    if (isServerType(param.getType())) {
                        return launcher.getFactory().Code().createVariableRead(param.getReference(), false);
                    }
                }
            } else if (parent instanceof CtConstructor) {
                CtConstructor constructor = (CtConstructor) parent;
                for (CtParameter param : constructor.getParameters()) {
                    if (isServerType(param.getType())) {
                        return launcher.getFactory().Code().createVariableRead(param.getReference(), false);
                    }
                }
            } else if (parent instanceof CtClass) {
                // Look for Server field in the class
                CtClass ctClass = (CtClass) parent;
                for (CtField field : ctClass.getFields()) {
                    if (isServerType(field.getType())) {
                        return launcher.getFactory().Code().createFieldRead(
                            launcher.getFactory().createThisAccess(ctClass.getReference()),
                            field.getReference()
                        );
                    }
                }
            }
            parent = parent.getParent();
        }
        
        return null;
    }
    
    private boolean isServerType(CtTypeReference typeRef) {
        return typeRef != null && "org.eclipse.jetty.server.Server".equals(typeRef.getQualifiedName());
    }
    
    private void updateServletImports(CtModel model) {
        System.out.println("2. Updating javax.servlet to jakarta.servlet...");
        
        // Update type references
        List<CtTypeReference> typeRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference typeRef) {
                String qname = typeRef.getQualifiedName();
                return qname != null && qname.startsWith("javax.servlet");
            }
        });
        
        for (CtTypeReference typeRef : typeRefs) {
            String newName = typeRef.getQualifiedName().replace("javax.servlet", "jakarta.servlet");
            System.out.println("  Updating: " + typeRef.getQualifiedName() + " -> " + newName);
            typeRef.setQualifiedName(newName);
            changeCount++;
        }
    }
    
    private void fixAbstractHandlerSubclasses(CtModel model) {
        System.out.println("3. Fixing AbstractHandler subclasses...");
        
        List<CtClass> handlerClasses = model.getElements(new TypeFilter<CtClass>(CtClass.class) {
            @Override
            public boolean matches(CtClass ctClass) {
                CtTypeReference superClass = ctClass.getSuperclass();
                return superClass != null && 
                    "org.eclipse.jetty.server.handler.AbstractHandler".equals(superClass.getQualifiedName());
            }
        });
        
        for (CtClass handlerClass : handlerClasses) {
            System.out.println("  Processing: " + handlerClass.getQualifiedName());
            
            // The handle method should already have correct parameter types
            // after updating imports, but we need to ensure it overrides correctly
            for (CtMethod method : handlerClass.getMethods()) {
                if ("handle".equals(method.getSimpleName()) && method.getParameters().size() >= 4) {
                    // Check if the method has correct @Override annotation
                    boolean hasOverride = method.getAnnotations().stream()
                        .anyMatch(ann -> ann.getAnnotationType().getQualifiedName().equals("java.lang.Override"));
                    
                    if (!hasOverride) {
                        // Add @Override annotation
                        CtAnnotation overrideAnn = launcher.getFactory().Annotation()
                            .createAnnotation(launcher.getFactory().Type().createReference("java.lang.Override"));
                        method.addAnnotation(overrideAnn);
                        System.out.println("    Added @Override annotation to handle method");
                        changeCount++;
                    }
                }
            }
        }
    }
    
    private void updateMethodCalls(CtModel model) {
        System.out.println("4. Updating method calls with changed signatures...");
        
        // Note: setSendServerVersion and setSendDateHeader still exist on Server
        // setPort and getLocalPort exist on NetworkConnector interface
        // The main issue is SelectChannelConnector -> ServerConnector which we already handled
        
        // If there were any other method signature changes, they would be handled here
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Jetty11MigrationTransformation <source-directory>");
            System.err.println("Example: java -cp spoon-transformation.jar github.chains.Jetty11MigrationTransformation /path/to/project");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Jetty 11 migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        Jetty11MigrationTransformation transformation = new Jetty11MigrationTransformation(launcher);
        int changes = transformation.apply(model);
        
        if (changes > 0) {
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("\nTransformation complete! Applied " + changes + " changes.");
            System.out.println("Note: Some changes may require manual review, especially ServerConnector constructors.");
        } else {
            System.out.println("No transformations applied.");
        }
    }
}