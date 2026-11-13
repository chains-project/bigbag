package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic transformation rule for javax.validation to jakarta.validation migration.
 * 
 * Breaking Change Characterization:
 * - Old API pattern: javax.validation.* (imports and type references)
 * - New API pattern: jakarta.validation.*
 * - Transformation: Replace "javax.validation" with "jakarta.validation" in imports and type references
 * 
 * This rule is generic and reusable for any project affected by the Jakarta EE migration
 * from Java EE (javax) to Jakarta EE (jakarta) namespace for validation API.
 * 
 * USAGE: java -jar transformation.jar <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("This transformation migrates javax.validation to jakarta.validation");
            System.err.println("Example: java -jar transformation.jar /path/to/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== javax.validation to jakarta.validation Migration ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Transformation: javax.validation -> jakarta.validation");
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            CtModel model = launcher.buildModel();
            
            int importTransformations = 0;
            
            // Transform imports - matches javax.validation.* pattern
            List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
            for (CtImport ctImport : imports) {
                String importStr = ctImport.getReference().toString();
                if (importStr.startsWith("javax.validation")) {
                    String newImport = importStr.replace("javax.validation", "jakarta.validation");
                    try {
                        ctImport.setReference(launcher.getFactory().createReference(newImport));
                        importTransformations++;
                        System.out.println("✓ " + importStr + " -> " + newImport);
                    } catch (Exception e) {
                        System.err.println("✗ Failed to transform import: " + importStr);
                    }
                }
            }
            
            System.out.println("\n=== Transformation Complete ===");
            System.out.println("Import transformations: " + importTransformations);
            System.out.println("\nNote: The transformation handles import statements.");
            System.out.println("For fully-qualified references in code (rare), additional");
            System.out.println("text replacement may be needed.");
            
            // Output to transformed directory
            String outputDir = sourceDir + "-transformed";
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("\nOutput written to: " + outputDir);
            System.out.println("To apply in-place: Replace original files with transformed ones.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            System.err.println("\nAlternative approach:");
            System.err.println("For simple projects, use: find . -name '*.java' -exec sed -i 's/javax.validation/jakarta.validation/g' {} \\;");
            System.exit(1);
        }
    }
}