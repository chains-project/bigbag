package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

/**
 * Generic Spoon transformation to fix breaking API changes where raw type arguments
 * in return types become wildcard type arguments.
 * 
 * Example breaking change:
 * - Old API: method returns `SortedMap<MOScope, ManagedObject>` (raw ManagedObject)
 * - New API: method returns `SortedMap<MOScope, ManagedObject<?>>` (with wildcard)
 * 
 * This transformation finds all occurrences of the specified type (e.g., ManagedObject)
 * when used as a raw type argument in generics and adds a wildcard `<?>`.
 * 
 * Usage: java Main <sourceDir> <outputDir>
 * 
 * Configuration: Modify the TARGET_TYPE constant below to specify which type to fix.
 */
public class Main {
    // The fully-qualified name of the type that needs wildcard added
    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir>");
            System.err.println("Fixes " + TARGET_TYPE + " raw type references by adding <?> wildcard");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying transformation to fix " + TARGET_TYPE + "<?> compatibility issues");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Add input resources
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Create a processor to fix type references
        launcher.addProcessor(new WildcardTypeFixer());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static class WildcardTypeFixer extends spoon.processing.AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeRef) {
            // Check if this is a reference to the target type
            if (typeRef != null && typeRef.getQualifiedName() != null &&
                typeRef.getQualifiedName().equals(TARGET_TYPE)) {
                
                // Check if ManagedObject is used without type arguments (raw)
                if (typeRef.getActualTypeArguments().isEmpty()) {
                    CtElement parent = typeRef.getParent();
                    
                    // Skip if this is in an implements or extends clause
                    // Check if parent is a type declaration (class/interface) that this type implements/extends
                    if (parent instanceof spoon.reflect.declaration.CtType) {
                        // This type reference is directly in a type declaration (implements/extends clause)
                        // Leave it raw - can't use wildcards in implements/extends
                        return;
                    }
                    
                    // Check if parent is a super type reference (extends/implements of a type)
                    if (parent instanceof spoon.reflect.reference.CtTypeReference) {
                        CtTypeReference<?> parentRef = (CtTypeReference<?>) parent;
                        // If this type is in the type arguments of another type reference,
                        // we need to check if that parent type reference is in an implements/extends
                        CtElement grandParent = parent.getParent();
                        if (grandParent instanceof spoon.reflect.declaration.CtType) {
                            // Parent type reference is in implements/extends clause
                            return;
                        }
                    }
                    
                    // Add wildcard type argument: ManagedObject -> ManagedObject<?>
                    typeRef.setActualTypeArguments(java.util.Collections.singletonList(
                        getFactory().createWildcardReference()
                    ));
                    
                    if (parent != null) {
                        System.out.println("Fixed " + TARGET_TYPE + " type reference at: " + 
                                         typeRef.getPosition() + 
                                         " in " + parent.getClass().getSimpleName());
                    }
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            if (candidate == null || candidate.getQualifiedName() == null) {
                return false;
            }
            return candidate.getQualifiedName().equals(TARGET_TYPE);
        }
    }
}