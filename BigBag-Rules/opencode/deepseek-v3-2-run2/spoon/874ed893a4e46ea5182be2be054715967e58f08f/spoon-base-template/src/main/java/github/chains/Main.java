package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class Main {
    
    /**
     * Configuration for API transformations.
     * Each entry defines: old API pattern -> new API pattern
     */
    private static final Map<String, Transformation> TRANSFORMATIONS = new HashMap<>();
    
    static {
        // Jackson 2.14+ breaking changes
        TRANSFORMATIONS.put(
            "com.fasterxml.jackson.databind.DeserializationConfig",
            new ConstructorToFactory("createCopy")
        );
        TRANSFORMATIONS.put(
            "com.fasterxml.jackson.databind.SerializationConfig", 
            new ConstructorToFactory("createCopy")
        );
        TRANSFORMATIONS.put(
            "com.fasterxml.jackson.databind.ObjectMapper.DefaultTypeResolverBuilder",
            new TypeReplacement("com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder")
        );
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            System.err.println("\nThis transformation fixes Jackson 2.14+ breaking changes:");
            System.err.println("1. DeserializationConfig constructors -> DeserializationConfig.createCopy()");
            System.err.println("2. SerializationConfig constructors -> SerializationConfig.createCopy()");
            System.err.println("3. DefaultTypeResolverBuilder -> StdTypeResolverBuilder");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying generic API transformation rules to: " + sourceDir);
        System.out.println("Target APIs: " + String.join(", ", TRANSFORMATIONS.keySet()));
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true); // Use no-classpath for broader compatibility
        launcher.addInputResource(sourceDir);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Apply all configured transformations
            int changes = applyTransformations(model);
            
            if (changes > 0) {
                // Output transformed code
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Transformation completed. Applied " + changes + " changes.");
            } else {
                System.out.println("No matching API patterns found. No changes applied.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Apply all configured transformations to the model.
     */
    private static int applyTransformations(CtModel model) {
        int totalChanges = 0;
        
        for (Map.Entry<String, Transformation> entry : TRANSFORMATIONS.entrySet()) {
            String targetType = entry.getKey();
            Transformation transformation = entry.getValue();
            
            List<CtConstructorCall<?>> constructorCalls = findConstructorCalls(model, targetType);
            
            for (CtConstructorCall<?> call : constructorCalls) {
                System.out.println("Found " + targetType + " constructor call at: " + 
                    getPositionInfo(call));
                
                if (transformation.apply(call)) {
                    totalChanges++;
                    System.out.println("  -> Transformed to: " + transformation.getDescription());
                }
            }
            
            if (!constructorCalls.isEmpty()) {
                System.out.println("Processed " + constructorCalls.size() + " " + targetType + " constructor calls");
            }
        }
        
        return totalChanges;
    }
    
    /**
     * Find all constructor calls for a specific type.
     */
    private static List<CtConstructorCall<?>> findConstructorCalls(CtModel model, String targetType) {
        return model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> typeRef = constructorCall.getType();
                return typeRef != null && targetType.equals(typeRef.getQualifiedName());
            }
        });
    }
    
    /**
     * Get position information for a constructor call.
     */
    private static String getPositionInfo(CtConstructorCall<?> call) {
        if (call.getPosition() != null && call.getPosition().isValidPosition()) {
            return call.getPosition().getFile().getName() + ":" + call.getPosition().getLine();
        }
        return "unknown position";
    }
    
    /**
     * Interface for transformation strategies.
     */
    private interface Transformation {
        boolean apply(CtConstructorCall<?> call);
        String getDescription();
    }
    
    /**
     * Transformation: constructor call -> factory method call.
     */
    private static class ConstructorToFactory implements Transformation {
        private final String factoryMethod;
        
        ConstructorToFactory(String factoryMethod) {
            this.factoryMethod = factoryMethod;
        }
        
        @Override
        public boolean apply(CtConstructorCall<?> call) {
            try {
                CtTypeReference<?> typeRef = call.getType();
                if (typeRef == null) return false;
                
                // Create type access for static method call
                var typeAccess = call.getFactory().Code().createTypeAccess(typeRef.clone());
                
                // Create method reference
                var methodRef = call.getFactory().createExecutableReference();
                methodRef.setDeclaringType(typeRef.clone());
                methodRef.setSimpleName(factoryMethod);
                
                // Create factory method invocation
                CtInvocation<?> factoryCall = call.getFactory().Code()
                    .createInvocation(
                        typeAccess,
                        methodRef,
                        new ArrayList<>(call.getArguments())
                    );
                
                call.replace(factoryCall);
                return true;
                
            } catch (Exception e) {
                System.err.println("  -> Failed to transform: " + e.getMessage());
                return false;
            }
        }
        
        @Override
        public String getDescription() {
            return "factory method " + factoryMethod + "()";
        }
    }
    
    /**
     * Transformation: replace type in constructor call.
     */
    private static class TypeReplacement implements Transformation {
        private final String newType;
        
        TypeReplacement(String newType) {
            this.newType = newType;
        }
        
        @Override
        public boolean apply(CtConstructorCall<?> call) {
            try {
                // Update the type reference
                CtTypeReference<?> newTypeRef = call.getFactory().createReference(newType);
                call.setType(newTypeRef);
                return true;
                
            } catch (Exception e) {
                System.err.println("  -> Failed to replace type: " + e.getMessage());
                return false;
            }
        }
        
        @Override
        public String getDescription() {
            return "new type " + newType;
        }
    }
}