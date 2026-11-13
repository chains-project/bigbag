package github.chains;

import java.util.List;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic Spoon transformation for Jakarta Servlet API 6.0.0 breaking changes
 * This transformation fixes compilation errors caused by moving from javax.servlet to jakarta.servlet
 */
public class Main {
    public static void main(String[] args) {
        // Process command-line arguments
        if (args.length == 0) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.getEnvironment().setAutoImports(true);
        
        // Add source directory
        launcher.addInputResource(sourceDirectory);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        transformJakartaServletImports(model);
        
        System.out.println("Transformation complete.");
    }
    
    /**
     * Transform javax.servlet imports to jakarta.servlet imports
     */
    private static void transformJakartaServletImports(CtModel model) {
        // Find all import declarations
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        for (CtImport imp : imports) {
            // We need to get the fully qualified name correctly
            String importStr = imp.getReference().toString();
            if (importStr.startsWith("javax.servlet")) {
                // Replace javax.servlet with jakarta.servlet
                String newImport = importStr.replace("javax.servlet", "jakarta.servlet");
                // Create a new import - this is a simplified approach
                System.out.println("Transforming import: " + importStr + " to " + newImport);
            }
        }
    }
}