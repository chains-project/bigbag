package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation for Jakarta EE 9+ migration:
 * Transforms javax.validation.* references to jakarta.validation.*
 * 
 * This transformation handles the breaking change where Java EE packages
 * moved from javax.* to jakarta.* namespace in Jakarta EE 9+.
 * 
 * Usage: java Main <source-directory> <output-directory>
 * Example: java Main /path/to/source /path/to/output
 * 
 * The transformation:
 * 1. Scans all Java files in the source directory
 * 2. Finds all type references starting with "javax.validation"
 * 3. Replaces them with "jakarta.validation" equivalents
 * 
 * Note: Due to Spoon API limitations with import statements, 
 * import statements may need additional text replacement:
 *   sed -i 's/import javax\.validation/import jakarta.validation/g' *.java
 * 
 * This transformation is reusable for any project affected by the
 * javax.validation -> jakarta.validation migration.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-directory> <output-directory>");
            System.err.println("Example: java Main /path/to/source /path/to/output");
            System.err.println("\nTransforms javax.validation.* references to jakarta.validation.*");
            System.err.println("for Jakarta EE 9+ migration.");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];

        System.out.println("Transforming javax.validation -> jakarta.validation migration");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);

        // Build model
        CtModel model = launcher.buildModel();

        // Apply transformation
        transformValidationReferences(model);

        // Pretty-print the transformed code
        launcher.prettyprint();

        System.out.println("\nTransformation complete!");
        System.out.println("\nNote: Import statements may need additional text replacement:");
        System.out.println("  sed -i 's/import javax\\.validation/import jakarta.validation/g' " + outputDir + "/**/*.java");
        System.out.println("\nThe transformation has updated type references from javax.validation.*");
        System.out.println("to jakarta.validation.* throughout the codebase.");
    }

    /**
     * Transforms all javax.validation.* type references to jakarta.validation.*
     * 
     * This method:
     * 1. Finds all type references in the model
     * 2. Identifies those starting with "javax.validation"
     * 3. Creates new references with "jakarta.validation" prefix
     * 4. Replaces the old references with new ones
     * 
     * @param model The Spoon model of the codebase
     */
    private static void transformValidationReferences(CtModel model) {
        // Find all type references in the model
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        int transformedCount = 0;
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("javax.validation")) {
                // Replace javax.validation with jakarta.validation
                String newQualifiedName = qualifiedName.replace("javax.validation", "jakarta.validation");
                
                System.out.println("Transforming: " + qualifiedName + " -> " + newQualifiedName);
                
                // Create new reference and replace old one
                CtTypeReference<?> newRef = typeRef.getFactory().createReference(newQualifiedName);
                typeRef.replace(newRef);
                transformedCount++;
            }
        }
        
        System.out.println("\nTransformed " + transformedCount + " type references.");
        
        // Note: Import statements are not easily accessible via Spoon's public API
        // Additional text replacement is needed for import statements:
        //   import javax.validation.* -> import jakarta.validation.*
    }
}