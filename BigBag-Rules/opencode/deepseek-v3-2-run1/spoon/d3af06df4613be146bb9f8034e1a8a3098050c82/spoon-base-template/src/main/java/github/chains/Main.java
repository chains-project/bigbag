package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;

import java.util.List;

/**
 * Generic transformation rule for Jetty 9/10 to Jetty 11+ migration.
 * 
 * Breaking changes handled:
 * 1. SelectChannelConnector class removed → replaced by ServerConnector
 *    Pattern: new SelectChannelConnector() → new ServerConnector(server)
 *    
 * 2. javax.servlet → jakarta.servlet package change
 *    Pattern: javax.servlet.* → jakarta.servlet.*
 *    
 * 3. AbstractHandler.handle method signature change
 *    Pattern: handle(String, Request, HttpServletRequest, HttpServletResponse)
 *           → handle(String, Request, HttpServletRequest, HttpServletResponse)
 *             (with jakarta.servlet types)
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("\nThis transformation handles Jetty 11 breaking changes:");
            System.err.println("1. SelectChannelConnector → ServerConnector");
            System.err.println("2. javax.servlet → jakarta.servlet");
            System.err.println("3. AbstractHandler method signature updates");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Jetty 11 Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            int changes = applyTransformations(model, launcher);
            
            if (changes > 0) {
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("\n✓ Transformation complete! Applied " + changes + " changes.");
                System.out.println("\nImportant notes:");
                System.out.println("1. ServerConnector constructors need a Server parameter");
                System.out.println("2. Check that Server instance is available in context");
                System.out.println("3. Verify imports are correct after transformation");
            } else {
                System.out.println("\nNo transformations needed.");
            }
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int applyTransformations(CtModel model, Launcher launcher) {
        int totalChanges = 0;
        
        // Transformation 1: Update javax.servlet to jakarta.servlet
        totalChanges += updateServletPackage(model);
        
        // Transformation 2: Replace SelectChannelConnector with ServerConnector
        totalChanges += replaceSelectChannelConnector(model, launcher);
        
        // Transformation 3: Fix AbstractHandler.handle method if needed
        totalChanges += fixAbstractHandlerMethods(model, launcher);
        
        return totalChanges;
    }
    
    private static int updateServletPackage(CtModel model) {
        System.out.println("\n[1] Updating javax.servlet to jakarta.servlet...");
        int changes = 0;
        
        // Find all type references starting with javax.servlet
        List<CtTypeReference> servletRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference typeRef) {
                String qname = typeRef.getQualifiedName();
                return qname != null && qname.startsWith("javax.servlet");
            }
        });
        
        for (CtTypeReference typeRef : servletRefs) {
            String oldName = typeRef.getQualifiedName();
            String newName = oldName.replace("javax.servlet", "jakarta.servlet");
            System.out.println("  " + oldName + " → " + newName);
            typeRef.setQualifiedName(newName);
            changes++;
        }
        
        System.out.println("  Updated " + changes + " type references");
        return changes;
    }
    
    private static int replaceSelectChannelConnector(CtModel model, Launcher launcher) {
        System.out.println("\n[2] Replacing SelectChannelConnector with ServerConnector...");
        int changes = 0;
        
        // Update type references
        List<CtTypeReference> connectorRefs = model.getElements(new TypeFilter<CtTypeReference>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference typeRef) {
                String qname = typeRef.getQualifiedName();
                return qname != null && qname.equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
            }
        });
        
        for (CtTypeReference typeRef : connectorRefs) {
            System.out.println("  Type reference: " + typeRef.getQualifiedName() + " → org.eclipse.jetty.server.ServerConnector");
            typeRef.setQualifiedName("org.eclipse.jetty.server.ServerConnector");
            changes++;
        }
        
        // Find constructor calls: new SelectChannelConnector()
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall constructorCall) {
                CtTypeReference typeRef = constructorCall.getType();
                return typeRef != null && 
                    "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(typeRef.getQualifiedName());
            }
        });
        
        for (CtConstructorCall constructorCall : constructorCalls) {
            String location = constructorCall.getPosition().getCompilationUnit().getMainType().getQualifiedName() +
                ":" + constructorCall.getPosition().getLine();
            System.out.println("  Constructor at " + location);
            
            // Try to find a Server variable in the constructor's context
            CtExpression serverVariable = findServerVariable(constructorCall, launcher);
            
            if (serverVariable != null) {
                // Replace: new SelectChannelConnector() → new ServerConnector(serverVariable)
                CtConstructorCall newConstructor = launcher.getFactory().Code()
                    .createConstructorCall(
                        launcher.getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector"),
                        serverVariable
                    );
                constructorCall.replace(newConstructor);
                System.out.println("    → new ServerConnector(" + getExpressionText(serverVariable) + ")");
                changes++;
            } else {
                System.out.println("    ⚠ Could not find Server variable - needs manual fix");
                System.out.println("    Replace with: new ServerConnector(server)");
            }
        }
        
        return changes;
    }
    
    private static CtExpression findServerVariable(CtConstructorCall constructorCall, Launcher launcher) {
        // Look for a Server variable in the same method/constructor
        CtElement parent = constructorCall.getParent();
        
        // First, get the containing executable (method or constructor)
        CtExecutable executable = null;
        while (parent != null) {
            if (parent instanceof CtMethod) {
                executable = (CtMethod) parent;
                break;
            } else if (parent instanceof CtConstructor) {
                executable = (CtConstructor) parent;
                break;
            }
            parent = parent.getParent();
        }
        
        if (executable != null) {
            // Look for Server parameters
            for (CtParameter param : executable.getParameters()) {
                if (isServerType(param.getType())) {
                    return launcher.getFactory().Code().createVariableRead(param.getReference(), false);
                }
            }
            
            // Look for Server fields in the containing class
            CtType containingType = executable.getParent(CtType.class);
            if (containingType != null) {
                for (CtField field : containingType.getFields()) {
                    if (isServerType(field.getType())) {
                        // Create field read: this.fieldName
                        CtThisAccess thisAccess = launcher.getFactory().createThisAccess(containingType.getReference());
                        return launcher.getFactory().Code().createFieldRead(thisAccess, field.getReference());
                    }
                }
            }
            
            // Look for local Server variables in the executable body
            List<CtLocalVariable> localVars = executable.getElements(new TypeFilter<CtLocalVariable>(CtLocalVariable.class) {
                @Override
                public boolean matches(CtLocalVariable localVar) {
                    return isServerType(localVar.getType());
                }
            });
            
            if (!localVars.isEmpty()) {
                // Use the first Server variable found
                return launcher.getFactory().Code().createVariableRead(localVars.get(0).getReference(), false);
            }
        }
        
        return null;
    }
    
    private static boolean isServerType(CtTypeReference typeRef) {
        if (typeRef == null) return false;
        String qname = typeRef.getQualifiedName();
        return qname != null && qname.equals("org.eclipse.jetty.server.Server");
    }
    
    private static String getExpressionText(CtExpression expression) {
        if (expression instanceof CtVariableRead) {
            return ((CtVariableRead) expression).getVariable().getSimpleName();
        } else if (expression instanceof CtFieldRead) {
            CtFieldRead fieldRead = (CtFieldRead) expression;
            if (fieldRead.getTarget() instanceof CtThisAccess) {
                return "this." + fieldRead.getVariable().getSimpleName();
            }
            return fieldRead.toString();
        }
        return expression.toString();
    }
    
    private static int fixAbstractHandlerMethods(CtModel model, Launcher launcher) {
        System.out.println("\n[3] Checking AbstractHandler methods...");
        int changes = 0;
        
        // Find classes extending AbstractHandler
        List<CtClass> handlerClasses = model.getElements(new TypeFilter<CtClass>(CtClass.class) {
            @Override
            public boolean matches(CtClass ctClass) {
                CtTypeReference superClass = ctClass.getSuperclass();
                return superClass != null && 
                    "org.eclipse.jetty.server.handler.AbstractHandler".equals(superClass.getQualifiedName());
            }
        });
        
        for (CtClass handlerClass : handlerClasses) {
            System.out.println("  Class: " + handlerClass.getQualifiedName());
            
            for (CtMethod method : handlerClass.getMethods()) {
                if ("handle".equals(method.getSimpleName()) && method.getParameters().size() == 4) {
                    // Check if method already has @Override annotation
                    boolean hasOverride = false;
                    for (CtAnnotation annotation : method.getAnnotations()) {
                        if (annotation.getAnnotationType().getQualifiedName().equals("java.lang.Override")) {
                            hasOverride = true;
                            break;
                        }
                    }
                    
                    if (!hasOverride) {
                        // Add @Override annotation
                        CtAnnotation overrideAnn = launcher.getFactory().Annotation()
                            .createAnnotation(launcher.getFactory().Type().createReference("java.lang.Override"));
                        method.addAnnotation(overrideAnn);
                        System.out.println("    + Added @Override annotation");
                        changes++;
                    }
                    
                    // Check parameter types
                    for (CtParameter param : method.getParameters()) {
                        if (param.getType().getQualifiedName().contains("javax.servlet")) {
                            System.out.println("    ⚠ Parameter still uses javax.servlet: " + param.getSimpleName());
                            System.out.println("      Should be updated to jakarta.servlet");
                        }
                    }
                }
            }
        }
        
        return changes;
    }
}