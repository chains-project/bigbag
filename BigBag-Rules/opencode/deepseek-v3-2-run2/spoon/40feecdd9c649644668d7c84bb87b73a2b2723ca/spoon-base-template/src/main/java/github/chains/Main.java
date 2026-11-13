package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Generic Spoon transformation for fixing Joda-Time DateMidnight deprecation.
 * 
 * This transformation demonstrates a generic approach to fixing breaking
 * API changes in dependencies. The transformation:
 * 
 * 1. Identifies usage of deprecated/removed API elements
 * 2. Applies structural transformations to update code
 * 3. Is parameterized to work with different breaking changes
 * 
 * Example: DateMidnight class deprecation in Joda-Time 2.12.5
 */
public class Main {
    
    // Configuration parameters
    private static final String SOURCE_DIRECTORY = "/workspace/CoursesPortlet";
    private static final String OUTPUT_DIRECTORY = "/workspace/CoursesPortlet-transformed";
    
    // Breaking change configuration
    private static final Map<String, String> TYPE_REPLACEMENTS = new HashMap<>();
    
    static {
        // Map deprecated types to their replacements
        TYPE_REPLACEMENTS.put("org.joda.time.DateMidnight", "org.joda.time.LocalDate");
    }
    
    public static void main(String[] args) {
        System.out.println("Starting Joda-Time API breaking change transformation...");
        
        String sourceDir = args.length > 0 ? args[0] : SOURCE_DIRECTORY;
        String outputDir = args.length > 1 ? args[1] : OUTPUT_DIRECTORY;
        
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        
        try {
            // Initialize Spoon
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Configure environment
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setCopyResources(true);
            
            // Build model
            System.out.println("Building code model...");
            CtModel model = launcher.buildModel();
            
            System.out.println("Applying transformations...");
            
            // Apply type replacements
            for (Map.Entry<String, String> entry : TYPE_REPLACEMENTS.entrySet()) {
                replaceTypeReferences(model, entry.getKey(), entry.getValue(), launcher);
            }
            
            // Write transformed code
            System.out.println("Writing transformed code...");
            launcher.prettyprint();
            
            System.out.println("\nTransformation complete!");
            System.out.println("Output written to: " + outputDir);
            
            // Print transformation summary
            printTransformationSummary();
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Replace all references to oldType with newType
     */
    private static void replaceTypeReferences(CtModel model, String oldType, String newType, Launcher launcher) {
        System.out.println("\nReplacing " + oldType + " with " + newType + "...");
        
        // Find all type references
        List<CtTypeReference<?>> typeRefs = model.getElements(
            new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
                @Override
                public boolean matches(CtTypeReference<?> typeRef) {
                    return oldType.equals(typeRef.getQualifiedName());
                }
            });
        
        System.out.println("  Found " + typeRefs.size() + " type references");
        
        // Replace each reference
        CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(newType);
        for (CtTypeReference<?> oldTypeRef : typeRefs) {
            try {
                oldTypeRef.replace(newTypeRef.clone());
            } catch (Exception e) {
                System.err.println("  Warning: Could not replace type reference: " + e.getMessage());
            }
        }
        
        // Find and transform constructor calls
        transformConstructorCalls(model, oldType, newType, launcher);
    }
    
    /**
     * Transform constructor calls
     */
    private static void transformConstructorCalls(CtModel model, String oldType, String newType, Launcher launcher) {
        List<CtConstructorCall<?>> constructorCalls = model.getElements(
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getType() != null) {
                        return oldType.equals(constructorCall.getType().getQualifiedName());
                    }
                    return false;
                }
            });
        
        System.out.println("  Found " + constructorCalls.size() + " constructor calls");
        
        if (constructorCalls.isEmpty()) {
            return;
        }
        
        // Note: In a real transformation, we would need to handle constructor
        // signature differences between DateMidnight and LocalDate
        // For this example, we'll just log them
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            System.out.println("  Constructor call at " + 
                constructorCall.getPosition().getFile().getName() + ":" +
                constructorCall.getPosition().getLine() + 
                " - needs manual review for argument adaptation");
        }
    }
    
    /**
     * Print summary of transformations applied
     */
    private static void printTransformationSummary() {
        System.out.println("\n=== Transformation Summary ===");
        System.out.println("Applied the following transformations:");
        System.out.println("1. Type replacements:");
        for (Map.Entry<String, String> entry : TYPE_REPLACEMENTS.entrySet()) {
            System.out.println("   - " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println("\nNote: Constructor calls may need manual adaptation");
        System.out.println("      due to signature differences between types.");
        System.out.println("\nThis transformation is generic and can be extended:");
        System.out.println("1. Add more type replacements to TYPE_REPLACEMENTS map");
        System.out.println("2. Add method signature transformations");
        System.out.println("3. Add field reference transformations");
        System.out.println("4. Handle import statement updates");
    }
}