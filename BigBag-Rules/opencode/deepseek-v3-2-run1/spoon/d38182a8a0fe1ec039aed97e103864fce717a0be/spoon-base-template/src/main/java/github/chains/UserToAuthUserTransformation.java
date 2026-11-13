package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation to fix the breaking change from Authentication.User to AuthUser.
 * 
 * This transformation handles:
 * 1. Type references to Authentication.User -> AuthUser
 * 2. Constructor calls new Authentication.User(...) -> new AuthUser(..., "")
 * 3. Type arguments in parameterized types (e.g., Optional<Authentication.User> -> Optional<AuthUser>)
 * 
 * The transformation is generic and can be applied to any Java project affected by this
 * dependency update.
 */
public class UserToAuthUserTransformation {
    
    private static final String OLD_TYPE = "com.artipie.http.auth.Authentication.User";
    private static final String NEW_TYPE = "com.artipie.http.auth.AuthUser";
    
    /**
     * Apply the transformation to a source directory.
     * 
     * @param sourceDir The source directory to transform
     */
    public void transform(String sourceDir) {
        System.out.println("Applying User->AuthUser transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            int changes = applyTransformation(model, factory);
            
            if (changes > 0) {
                launcher.prettyprint();
                System.out.println("Transformation completed successfully! Made " + changes + " changes.");
            } else {
                System.out.println("No changes needed - no Authentication.User references found.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Transformation failed", e);
        }
    }
    
    private int applyTransformation(CtModel model, Factory factory) {
        int changes = 0;
        
        // Create the new type reference
        CtTypeReference<?> newTypeRef = factory.Type().createReference(NEW_TYPE);
        
        // 1. Transform type references
        changes += transformTypeReferences(model, factory, newTypeRef);
        
        // 2. Transform constructor calls
        changes += transformConstructorCalls(model, factory, newTypeRef);
        
        // 3. Transform new class expressions (anonymous classes)
        changes += transformNewClassExpressions(model, factory, newTypeRef);
        
        return changes;
    }
    
    private int transformTypeReferences(CtModel model, Factory factory, CtTypeReference<?> newTypeRef) {
        int changes = 0;
        
        // Find all type references
        List<CtTypeReference<?>> allTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class));
        
        for (CtTypeReference<?> typeRef : allTypeRefs) {
            try {
                // Check if this is a direct reference to Authentication.User
                if (isAuthenticationUserType(typeRef)) {
                    typeRef.replace(newTypeRef.clone());
                    System.out.println("Replaced type reference: Authentication.User -> AuthUser");
                    changes++;
                    continue;
                }
                
                // Check type arguments in parameterized types
                if (typeRef.getActualTypeArguments() != null && !typeRef.getActualTypeArguments().isEmpty()) {
                    List<CtTypeReference<?>> newArgs = new ArrayList<>();
                    boolean changed = false;
                    
                    for (CtTypeReference<?> arg : typeRef.getActualTypeArguments()) {
                        if (isAuthenticationUserType(arg)) {
                            newArgs.add(newTypeRef.clone());
                            changed = true;
                        } else {
                            newArgs.add(arg);
                        }
                    }
                    
                    if (changed) {
                        CtTypeReference<?> cloned = typeRef.clone();
                        cloned.setActualTypeArguments(newArgs);
                        typeRef.replace(cloned);
                        System.out.println("Replaced type argument in parameterized type: Authentication.User -> AuthUser");
                        changes++;
                    }
                }
            } catch (Exception e) {
                // Silently continue - some type references might not be replaceable
            }
        }
        
        return changes;
    }
    
    private int transformConstructorCalls(CtModel model, Factory factory, CtTypeReference<?> newTypeRef) {
        int changes = 0;
        
        List<CtConstructorCall<?>> constructorCalls = model.getElements(
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    return isAuthenticationUserType(constructorCall.getType());
                }
            }
        );
        
        for (CtConstructorCall<?> oldCall : constructorCalls) {
            try {
                // Create new AuthUser constructor call
                CtConstructorCall<?> newCall = factory.createConstructorCall(newTypeRef);
                
                // Copy existing arguments (username)
                List<CtExpression<?>> args = new ArrayList<>(oldCall.getArguments());
                
                // Add empty string as context parameter
                CtLiteral<String> emptyContext = factory.createLiteral("");
                args.add(emptyContext);
                
                newCall.setArguments(args);
                
                // Replace the old constructor call with the new one
                oldCall.replace(newCall);
                System.out.println("Transformed constructor call: new Authentication.User(...) -> new AuthUser(..., \"\")");
                changes++;
                
            } catch (Exception e) {
                System.err.println("Warning: Could not transform constructor call: " + oldCall);
            }
        }
        
        return changes;
    }
    
    private int transformNewClassExpressions(CtModel model, Factory factory, CtTypeReference<?> newTypeRef) {
        int changes = 0;
        
        List<CtNewClass<?>> newClasses = model.getElements(
            new TypeFilter<CtNewClass<?>>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass<?> newClass) {
                    return isAuthenticationUserType(newClass.getType());
                }
            }
        );
        
        for (CtNewClass<?> oldNewClass : newClasses) {
            try {
                // For new class expressions (anonymous classes extending Authentication.User),
                // we convert them to constructor calls
                CtConstructorCall<?> newCall = factory.createConstructorCall(newTypeRef);
                
                // Copy existing arguments (username)
                List<CtExpression<?>> args = new ArrayList<>(oldNewClass.getArguments());
                
                // Add empty string as context parameter
                CtLiteral<String> emptyContext = factory.createLiteral("");
                args.add(emptyContext);
                
                newCall.setArguments(args);
                
                // Replace the old new class with constructor call
                oldNewClass.replace(newCall);
                System.out.println("Transformed new class expression: new Authentication.User(...) {...} -> new AuthUser(..., \"\")");
                changes++;
                
            } catch (Exception e) {
                System.err.println("Warning: Could not transform new class expression: " + oldNewClass);
            }
        }
        
        return changes;
    }
    
    private boolean isAuthenticationUserType(CtTypeReference<?> typeRef) {
        return typeRef != null && 
               typeRef.getQualifiedName() != null && 
               typeRef.getQualifiedName().equals(OLD_TYPE);
    }
    
    /**
     * Main method for command-line execution.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Applies Authentication.User -> AuthUser transformation to the specified source directory.");
            System.exit(1);
        }
        
        UserToAuthUserTransformation transformer = new UserToAuthUserTransformation();
        transformer.transform(args[0]);
    }
}