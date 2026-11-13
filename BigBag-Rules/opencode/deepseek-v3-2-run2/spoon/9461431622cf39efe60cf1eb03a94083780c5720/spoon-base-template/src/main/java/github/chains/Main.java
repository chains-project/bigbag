package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ClassTypingContext;
import java.util.List;
import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar <jar> <source_directory>");
            System.out.println("GENERIC TRANSFORMATION: Converts raw parameterized types in SortedMap<K, V> to wildcard parameterized types");
            System.out.println("Specifically: SortedMap<MOScope, ManagedObject> -> SortedMap<MOScope, ManagedObject<?>>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Configuration parameters for the breaking change
        // These can be changed to adapt to different breaking API changes
        String containerType = "java.util.SortedMap";
        String rawInterfaceToFix = "org.snmp4j.agent.ManagedObject";
        
        System.out.println("Applying transformation for breaking API change:");
        System.out.println("  Container type: " + containerType);
        System.out.println("  Raw interface to parameterize: " + rawInterfaceToFix);
        System.out.println("  Transformation: " + containerType + "<K, " + rawInterfaceToFix + "> -> " + 
                           containerType + "<K, " + rawInterfaceToFix + "<?>>");
        
        SpoonAPI spoon = new Launcher();
        spoon.addInputResource(sourceDir);
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setAutoImports(true);
        spoon.getEnvironment().setCommentEnabled(true);
        
        try {
            // Build model
            spoon.buildModel();
            
            // Find all CtTypeReference instances matching the problematic pattern
            List<CtTypeReference<?>> refs = Query.getElements(spoon.getFactory(),
                new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                    @Override
                    public boolean matches(CtTypeReference<?> ref) {
                        // Must be a parameterized type with exactly 2 type arguments
                        if (ref.getActualTypeArguments() == null || ref.getActualTypeArguments().size() != 2) {
                            return false;
                        }
                        
                        // Check if it's the target container type (e.g., SortedMap)
                        String qualifiedName = ref.getQualifiedName();
                        if (!containerType.equals(qualifiedName)) {
                            return false;
                        }
                        
                        List<CtTypeReference<?>> typeArgs = ref.getActualTypeArguments();
                        CtTypeReference<?> valueType = typeArgs.get(1);
                        
                        // Check if value type is the raw interface that needs parameterization
                        if (valueType.getActualTypeArguments() == null || valueType.getActualTypeArguments().isEmpty()) {
                            // Raw type - check if it's the target interface
                            String valueTypeName = valueType.getQualifiedName();
                            // Match both fully qualified name and simple name (for noClasspath mode)
                            return rawInterfaceToFix.equals(valueTypeName) || 
                                   rawInterfaceToFix.endsWith("." + valueTypeName);
                        }
                        
                        return false;
                    }
                });
            
            System.out.println("Found " + refs.size() + " raw " + rawInterfaceToFix + 
                             " references in " + containerType + " types");
            
            int fixedCount = 0;
            for (CtTypeReference<?> ref : refs) {
                try {
                    // Get the value type (the raw interface)
                    CtTypeReference<?> rawInterfaceRef = ref.getActualTypeArguments().get(1);
                    
                    // Create unbounded wildcard <?>
                    CtWildcardReference wildcard = spoon.getFactory().createWildcardReference();
                    
                    // Clone the raw reference and add the wildcard type argument
                    CtTypeReference<?> parameterizedInterface = rawInterfaceRef.clone();
                    parameterizedInterface.addActualTypeArgument(wildcard);
                    
                    // Replace the raw type with parameterized type
                    ref.getActualTypeArguments().set(1, parameterizedInterface);
                    
                    System.out.println("Fixed raw " + rawInterfaceToFix + " in " + 
                                     containerType + " at position: " + ref.getPosition());
                    fixedCount++;
                } catch (Exception e) {
                    System.err.println("Error fixing reference at " + ref.getPosition() + ": " + e.getMessage());
                }
            }
            
            if (fixedCount > 0) {
                // Apply transformation to source files
                spoon.setSourceOutputDirectory(sourceDir + "/spooned");
                spoon.prettyprint();
                
                System.out.println("\nTransformation complete!");
                System.out.println("Fixed " + fixedCount + " occurrences");
                System.out.println("Modified files saved to: " + sourceDir + "/spooned");
                System.out.println("\nThis transformation can be applied to any project affected by the same breaking change.");
                System.out.println("To adapt to different breaking changes, modify the configuration parameters:");
                System.out.println("  - containerType: The generic container type (e.g., \"java.util.SortedMap\")");
                System.out.println("  - rawInterfaceToFix: The raw interface that needs parameterization (e.g., \"org.snmp4j.agent.ManagedObject\")");
            } else {
                System.out.println("No matching patterns found to fix.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}