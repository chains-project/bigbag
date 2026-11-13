package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.support.SpoonClassNotFoundException;

import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking API changes in snmp4j-agent 3.6.6.
 * 
 * Breaking Change: MOServer.getRegistry() now returns SortedMap<MOScope, ManagedObject<?>>
 * instead of SortedMap<MOScope, ManagedObject> (raw or parameterized without wildcard).
 * 
 * This transformation finds all occurrences of ManagedObject type in generic parameters
 * without wildcards and adds <?> wildcard to match the new API.
 * 
 * The transformation is generic and reusable for any project affected by this breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Applies generic transformation to add wildcards to ManagedObject type parameters");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process all Java files in the source directory
        System.out.println("Processing all Java files in: " + sourceDir);
        launcher.addInputResource(sourceDir);
        
        // Output transformed files to a separate directory
        String outputDir = sourceDir + "-transformed";
        launcher.setSourceOutputDirectory(outputDir);
        
        launcher.addProcessor(new ManagedObjectWildcardProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully");
            System.out.println("Transformed files written to: " + outputDir);
        } catch (SpoonClassNotFoundException e) {
            System.err.println("Warning: Some classes not found, but transformation may still work: " + e.getMessage());
            System.out.println("Transformation completed with warnings");
        } catch (spoon.compiler.ModelBuildingException e) {
            System.err.println("Warning: Model building issues (likely Lombok annotations), but transformation may still work: " + e.getMessage());
            System.out.println("Transformation completed with warnings");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor that adds wildcards to ManagedObject type parameters in generic types.
     * This handles the breaking change where methods returning collections of ManagedObject
     * now return collections of ManagedObject<?> instead.
     */
    static class ManagedObjectWildcardProcessor extends AbstractProcessor<CtElement> {
        
        @Override
        public void process(CtElement element) {
            // Process variable declarations
            if (element instanceof CtVariable) {
                CtVariable<?> variable = (CtVariable<?>) element;
                processTypeReference(variable.getType(), "variable " + variable.getSimpleName());
            }
            // Process field declarations  
            else if (element instanceof CtField) {
                CtField<?> field = (CtField<?>) element;
                processTypeReference(field.getType(), "field " + field.getSimpleName());
            }
            // Process method parameters
            else if (element instanceof CtParameter) {
                CtParameter<?> param = (CtParameter<?>) element;
                processTypeReference(param.getType(), "parameter " + param.getSimpleName());
            }
            // Process method return types
            else if (element instanceof CtMethod) {
                CtMethod<?> method = (CtMethod<?>) element;
                processTypeReference(method.getType(), "method " + method.getSimpleName() + " return type");
            }
            // Process local variables
            else if (element instanceof CtLocalVariable) {
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) element;
                processTypeReference(localVar.getType(), "local variable " + localVar.getSimpleName());
            }
        }
        
        private void processTypeReference(CtTypeReference<?> typeRef, String context) {
            if (typeRef == null) {
                return;
            }
            
            addWildcardToGenericType(typeRef, context);
            
            // Recursively process type arguments
            List<CtTypeReference<?>> typeArguments = typeRef.getActualTypeArguments();
            if (typeArguments != null) {
                for (CtTypeReference<?> arg : typeArguments) {
                    processTypeReference(arg, context + " type argument");
                }
            }
        }
        
        private void addWildcardToGenericType(CtTypeReference<?> typeRef, String context) {
            // First check if the type itself is a raw ManagedObject in a generic context
            if (typeRef instanceof spoon.reflect.reference.CtActualTypeContainer) {
                spoon.reflect.reference.CtActualTypeContainer container = 
                    (spoon.reflect.reference.CtActualTypeContainer) typeRef;
                List<CtTypeReference<?>> typeArguments = container.getActualTypeArguments();
                
                if (typeArguments != null && !typeArguments.isEmpty()) {
                    boolean modified = false;
                    for (int i = 0; i < typeArguments.size(); i++) {
                        CtTypeReference<?> typeArg = typeArguments.get(i);
                        
                        if (isManagedObjectType(typeArg) && !hasWildcard(typeArg)) {
                            System.out.println("Found ManagedObject without wildcard in " + context + 
                                " at " + (typeArg.getPosition() != null ? typeArg.getPosition() : "unknown position"));
                            
                            // Create ManagedObject<?> 
                            CtTypeReference<?> wildcardType = getFactory().Type()
                                .createReference("org.snmp4j.agent.ManagedObject")
                                .addActualTypeArgument(getFactory().Core().createWildcardReference());
                            
                            typeArguments.set(i, wildcardType);
                            modified = true;
                        }
                    }
                    
                    if (modified) {
                        container.setActualTypeArguments(typeArguments);
                    }
                }
            }
        }
        
        private boolean isManagedObjectType(CtTypeReference<?> typeRef) {
            if (typeRef == null) {
                return false;
            }
            
            String qualifiedName = typeRef.getQualifiedName();
            String simpleName = typeRef.getSimpleName();
            
            if (qualifiedName != null) {
                return qualifiedName.equals("org.snmp4j.agent.ManagedObject") ||
                       qualifiedName.startsWith("org.snmp4j.agent.ManagedObject<");
            }
            
            if (simpleName != null) {
                return simpleName.equals("ManagedObject");
            }
            
            return false;
        }
        
        private boolean hasWildcard(CtTypeReference<?> typeRef) {
            if (typeRef == null) {
                return false;
            }
            
            List<CtTypeReference<?>> actualTypeArgs = typeRef.getActualTypeArguments();
            if (actualTypeArgs != null && !actualTypeArgs.isEmpty()) {
                for (CtTypeReference<?> arg : actualTypeArgs) {
                    if (arg instanceof spoon.reflect.reference.CtWildcardReference) {
                        return true;
                    }
                }
            }
            return false;
        }
        
        @Override
        public void processingDone() {
            System.out.println("Processing completed for ManagedObject wildcard transformation");
        }
    }
}