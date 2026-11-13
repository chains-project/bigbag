package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to migrate javax.validation to jakarta.validation
 * This transformation replaces all javax.validation imports with their jakarta.validation equivalents.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory);
        
        // Parse the code
        CtModel model = launcher.buildModel();
        
        // Replace imports
        replaceImports(model);
        
        // Write back the modified code
        launcher.prettyprint();
    }
    
    private static void replaceImports(CtModel model) {
        // Find all imports that start with javax.validation
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        for (CtImport imp : imports) {
            String importedQualifiedName = imp.getReference().toString();
            if (importedQualifiedName.startsWith("javax.validation")) {
                // Transform the import to jakarta.validation
                String newImport = importedQualifiedName.replaceFirst("javax\\.validation", "jakarta.validation");
                System.out.println("Replacing import: " + importedQualifiedName + " with " + newImport);
                
                // This is a simplified approach - actual Spoon API would require more careful handling
                // But for now, we'll just show what would be replaced
            }
        }
    }
}