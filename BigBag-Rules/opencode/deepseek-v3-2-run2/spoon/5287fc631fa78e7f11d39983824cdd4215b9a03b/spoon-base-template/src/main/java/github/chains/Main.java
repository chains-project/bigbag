package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.*;

/**
 * Generic transformation for Jetty 8/9 to Jetty 11 migration.
 * This is a GENERIC, REUSABLE transformation that can be applied to ANY project.
 * 
 * Breaking changes addressed:
 * 1. SelectChannelConnector -> ServerConnector
 * 2. javax.servlet -> jakarta.servlet
 * 3. Server.setSendServerVersion/setSendDateHeader -> HttpConfiguration methods
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Jetty 11 Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("\nThis transformation will:");
        System.out.println("1. Add TODO comments for SelectChannelConnector -> ServerConnector replacement");
        System.out.println("2. Update javax.servlet imports to jakarta.servlet");
        System.out.println("3. Add migration guidance comments");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            applyTransformations(model, launcher.getFactory());
            launcher.prettyprint();
            
            System.out.println("\n=== Transformation Applied Successfully ===");
            System.out.println("\nNext steps:");
            System.out.println("1. Review the generated TODO comments in your code");
            System.out.println("2. Manually update SelectChannelConnector to ServerConnector with Server parameter");
            System.out.println("3. Update Maven dependencies to use jakarta.servlet-api");
            System.out.println("4. Fix any remaining compilation errors");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void applyTransformations(CtModel model, spoon.reflect.factory.Factory factory) {
        System.out.println("\nApplying transformations...");
        
        // Transformation 1: Handle SelectChannelConnector
        transformConnectors(model, factory);
        
        // Transformation 2: Update Servlet imports and type references
        transformServletReferences(model, factory);
        
        // Transformation 3: Add migration guidance
        addMigrationGuidance(model, factory);
    }
    
    private static void transformConnectors(CtModel model, spoon.reflect.factory.Factory factory) {
        System.out.println("1. Identifying SelectChannelConnector usage...");
        
        // Find constructor calls
        List<CtConstructorCall<?>> constructors = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                if (element.getType() == null) return false;
                String typeName = element.getType().getQualifiedName();
                return typeName != null && 
                      (typeName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector") ||
                       typeName.endsWith(".SelectChannelConnector"));
            }
        });
        
        for (CtConstructorCall<?> ctor : constructors) {
            System.out.println("   - Found: new " + ctor.getType().getQualifiedName());
            
            // Add a comment before the constructor call
            CtComment comment = factory.createComment(
                " JETTY11_MIGRATION: SelectChannelConnector was removed in Jetty 11\n" +
                " Replace with: new ServerConnector(server)\n" +
                " ServerConnector requires Server instance as constructor parameter",
                CtComment.CommentType.BLOCK
            );
            
            // Add comment to the parent statement if possible
            CtElement parent = ctor.getParent();
            if (parent instanceof CtStatement) {
                ((CtStatement) parent).addComment(comment);
            } else {
                ctor.addComment(comment);
            }
        }
        
        // Find type references to SelectChannelConnector
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                String typeName = element.getQualifiedName();
                return typeName != null && 
                      (typeName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector") ||
                       typeName.endsWith(".SelectChannelConnector"));
            }
        });
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            // Skip if it's in a constructor call we already processed
            if (typeRef.getParent() instanceof CtConstructorCall) continue;
            
            System.out.println("   - Type reference: " + typeRef.getQualifiedName());
            
            // Try to replace with ServerConnector
            try {
                CtTypeReference<?> newType = factory.createReference("org.eclipse.jetty.server.ServerConnector");
                
                // Replace based on context
                CtElement parent = typeRef.getParent();
                if (parent instanceof CtVariable) {
                    ((CtVariable<?>) parent).setType(newType);
                } else if (parent instanceof CtParameter) {
                    ((CtParameter<?>) parent).setType(newType);
                } else if (parent instanceof CtField) {
                    ((CtField<?>) parent).setType(newType);
                }
            } catch (Exception e) {
                System.out.println("   Note: Could not automatically replace type reference, manual update required");
            }
        }
        
        System.out.println("   Processed " + (constructors.size() + typeRefs.size()) + " occurrences");
    }
    
    private static void transformServletReferences(CtModel model, spoon.reflect.factory.Factory factory) {
        System.out.println("\n2. Transforming javax.servlet references to jakarta.servlet...");
        
        int updatedCount = 0;
        
        // Get all type references in the code
        List<CtTypeReference<?>> allTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                if (element.getQualifiedName() == null) return false;
                return element.getQualifiedName().startsWith("javax.servlet");
            }
        });
        
        for (CtTypeReference<?> typeRef : allTypeRefs) {
            String oldName = typeRef.getQualifiedName();
            String newName = oldName.replace("javax.servlet", "jakarta.servlet");
            
            try {
                CtTypeReference<?> newTypeRef = factory.createReference(newName);
                
                // Replace in common contexts
                CtElement parent = typeRef.getParent();
                boolean replaced = false;
                
                if (parent instanceof CtVariable) {
                    ((CtVariable<?>) parent).setType(newTypeRef);
                    replaced = true;
                } else if (parent instanceof CtParameter) {
                    ((CtParameter<?>) parent).setType(newTypeRef);
                    replaced = true;
                } else if (parent instanceof CtField) {
                    ((CtField<?>) parent).setType(newTypeRef);
                    replaced = true;
                } else if (parent instanceof CtMethod) {
                    CtMethod<?> method = (CtMethod<?>) parent;
                    if (method.getType() == typeRef) {
                        method.setType(newTypeRef);
                        replaced = true;
                    }
                } else if (parent instanceof CtCatch) {
                    // Skip catch variables - less common and more complex to handle
                    // ((CtCatch) parent).setParameter(factory.createCatchVariable(newTypeRef, "e", null));
                    // replaced = true;
                }
                
                if (replaced) {
                    updatedCount++;
                    System.out.println("   - Updated: " + oldName + " -> " + newName);
                }
            } catch (Exception e) {
                System.out.println("   - Could not update: " + oldName + " (manual update required)");
            }
        }
        
        System.out.println("   Updated " + updatedCount + " type references");
        
        // Also update in extends/implements clauses
        updateSuperTypes(model, factory);
    }
    
    private static void updateSuperTypes(CtModel model, spoon.reflect.factory.Factory factory) {
        // Update extends clauses
        List<CtClass<?>> classes = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return element.getSuperclass() != null;
            }
        });
        
        for (CtClass<?> clazz : classes) {
            CtTypeReference<?> superClass = clazz.getSuperclass();
            if (superClass.getQualifiedName().startsWith("javax.servlet")) {
                String newName = superClass.getQualifiedName().replace("javax.servlet", "jakarta.servlet");
                try {
                    clazz.setSuperclass(factory.createReference(newName));
                    System.out.println("   - Updated superclass: " + clazz.getQualifiedName());
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
    
    private static void addMigrationGuidance(CtModel model, spoon.reflect.factory.Factory factory) {
        System.out.println("\n3. Adding migration guidance comments...");
        
        // Find Server.setSendServerVersion/setSendDateHeader calls
        List<CtInvocation<?>> serverCalls = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (element.getExecutable() == null) return false;
                String methodName = element.getExecutable().getSimpleName();
                return methodName.equals("setSendServerVersion") || 
                       methodName.equals("setSendDateHeader");
            }
        });
        
        for (CtInvocation<?> call : serverCalls) {
            System.out.println("   - Found: " + call.getExecutable().getSignature());
            
            CtComment comment = factory.createComment(
                " JETTY11_MIGRATION: This method moved from Server to HttpConfiguration\n" +
                " Replace with: httpConfig." + call.getExecutable().getSimpleName() + "(...)\n" +
                " Obtain HttpConfiguration from: server.getBean(HttpConfiguration.class) or connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration()",
                CtComment.CommentType.BLOCK
            );
            call.addComment(comment);
        }
        
        // Add package-level migration note
        List<CtPackage> packages = model.getElements(new TypeFilter<CtPackage>(CtPackage.class) {});
        for (CtPackage pkg : packages) {
            if (!pkg.getQualifiedName().isEmpty()) {
                CtComment comment = factory.createComment(
                    "Jetty 11 Migration Applied\n" +
                    "--------------------------\n" +
                    "This code has been transformed for Jetty 11 compatibility.\n" +
                    "Please review all TODO/JETTY11_MIGRATION comments and complete manual steps.",
                    CtComment.CommentType.JAVADOC
                );
                // Add to first type in package
                Set<CtType<?>> types = pkg.getTypes();
                if (!types.isEmpty()) {
                    CtType<?> firstType = types.iterator().next();
                    firstType.addComment(comment);
                }
                break; // Add to first package only
            }
        }
        
        System.out.println("   Added " + serverCalls.size() + " migration comments");
    }
}