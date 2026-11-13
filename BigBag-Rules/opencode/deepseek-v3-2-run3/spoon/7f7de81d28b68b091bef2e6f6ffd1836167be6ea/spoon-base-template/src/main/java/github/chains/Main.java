package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.replace.ReplacementVisitor;
import java.util.List;
import java.util.ArrayList;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce verbosity
        
        try {
            CtModel model = launcher.buildModel();
            
            int transformations = 0;
            
            // Find all generic type references that might need fixing
            List<CtTypeReference<?>> genericTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    // We're looking for generic types with type arguments
                    List<CtTypeReference<?>> typeArgs = typeRef.getActualTypeArguments();
                    if (typeArgs != null && !typeArgs.isEmpty()) {
                        // Check if any type argument is raw ManagedObject
                        for (CtTypeReference<?> arg : typeArgs) {
                            if (arg != null && arg.getSimpleName() != null && 
                                arg.getSimpleName().equals("ManagedObject")) {
                                // Check if it's raw (no type arguments of its own)
                                if (arg.getActualTypeArguments().isEmpty()) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }
            });
            
            System.out.println("Found " + genericTypeRefs.size() + " generic types with raw ManagedObject");
            
            // Transform each found pattern
            for (CtTypeReference<?> genericTypeRef : genericTypeRefs) {
                try {
                    // Get all type arguments
                    List<CtTypeReference<?>> originalArgs = genericTypeRef.getActualTypeArguments();
                    List<CtTypeReference<?>> newArgs = new ArrayList<>();
                    
                    boolean changed = false;
                    
                    // Process each type argument
                    for (CtTypeReference<?> arg : originalArgs) {
                        if (arg != null && arg.getSimpleName() != null && 
                            arg.getSimpleName().equals("ManagedObject") &&
                            arg.getActualTypeArguments().isEmpty()) {
                            
                            // Create ManagedObject<?>
                            CtTypeReference<?> newArg = arg.clone();
                            CtWildcardReference wildcard = launcher.getFactory().Core().createWildcardReference();
                            wildcard.setBoundingType(launcher.getFactory().Type().OBJECT);
                            newArg.setActualTypeArguments(List.of(wildcard));
                            
                            newArgs.add(newArg);
                            changed = true;
                        } else {
                            newArgs.add(arg);
                        }
                    }
                    
                    if (changed) {
                        // Create new type reference with same qualified name
                        CtTypeReference<?> newTypeRef = launcher.getFactory().Type()
                            .createReference(genericTypeRef.getQualifiedName());
                        newTypeRef.setActualTypeArguments(newArgs);
                        
                        // Replace the old reference
                        genericTypeRef.replace(newTypeRef);
                        transformations++;
                        
                        System.out.println("Transformed: " + genericTypeRef + " to " + newTypeRef);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Failed to transform " + genericTypeRef + ": " + e.getMessage());
                }
            }
            
            // Also look for variable declarations with raw ManagedObject types
            // that might be assigned from methods returning ManagedObject<?>
            List<CtVariable<?>> variableDecls = model.getElements(new TypeFilter<CtVariable<?>>(CtVariable.class) {
                @Override
                public boolean matches(CtVariable<?> variable) {
                    CtTypeReference<?> typeRef = variable.getType();
                    if (typeRef != null && typeRef.getSimpleName() != null && 
                        typeRef.getSimpleName().equals("ManagedObject")) {
                        // Check if it's raw
                        if (typeRef.getActualTypeArguments().isEmpty()) {
                            return true;
                        }
                    }
                    return false;
                }
            });
            
            System.out.println("Found " + variableDecls.size() + " variable declarations with raw ManagedObject");
            
            // Transform variable declarations (more conservative - only if needed)
            for (CtVariable<?> variable : variableDecls) {
                try {
                    CtTypeReference<?> typeRef = variable.getType();
                    
                    // Create ManagedObject<?>
                    CtTypeReference<?> newTypeRef = typeRef.clone();
                    CtWildcardReference wildcard = launcher.getFactory().Core().createWildcardReference();
                    wildcard.setBoundingType(launcher.getFactory().Type().OBJECT);
                    newTypeRef.setActualTypeArguments(List.of(wildcard));
                    
                    variable.setType(newTypeRef);
                    transformations++;
                    
                    System.out.println("Transformed variable: " + variable.getSimpleName());
                    
                } catch (Exception e) {
                    System.err.println("Failed to transform variable: " + e.getMessage());
                }
            }
            
            // Write transformed code back
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Total transformations applied: " + transformations);
            System.out.println("Transformation complete!");
            
        } catch (Exception e) {
            System.err.println("Failed to build model: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}