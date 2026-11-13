package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * A generic Spoon transformation rule for package renaming.
 * This transformation replaces all references from an old package prefix to a new package prefix.
 * 
 * Usage: java -jar transformation.jar <source-directory> <old-package> <new-package>
 * Example: java -jar transformation.jar ./src develop.p2p.lib tokyo.peya.lib
 * 
 * The transformation handles:
 * 1. Import statements (both regular and static)
 * 2. Fully-qualified type references in code
 * 3. Constructor calls with fully-qualified types
 * 4. Method references with fully-qualified types
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar transformation.jar <source-directory> <old-package> <new-package>");
            System.err.println("Example: java -jar transformation.jar ./src develop.p2p.lib tokyo.peya.lib");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldPackage = args[1];
        String newPackage = args[2];
        
        System.out.println("Applying transformation:");
        System.out.println("  Source directory: " + sourceDir);
        System.out.println("  Old package: " + oldPackage);
        System.out.println("  New package: " + newPackage);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all type references to old package classes
        List<CtTypeReference<?>> oldTypeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                return element.getQualifiedName() != null && 
                       element.getQualifiedName().startsWith(oldPackage);
            }
        });
        
        System.out.println("Found " + oldTypeRefs.size() + " type references to replace");
        
        // Replace each type reference
        for (CtTypeReference<?> oldRef : oldTypeRefs) {
            String oldName = oldRef.getQualifiedName();
            String newName = oldName.replace(oldPackage, newPackage);
            
            // Create new type reference
            CtTypeReference<?> newRef = launcher.getFactory().createReference(newName);
            
            // Replace the reference in the AST
            oldRef.replace(newRef);
        }
        
        // Handle imports
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class));
        
        int importReplacements = 0;
        for (CtImport imp : imports) {
            CtReference ref = imp.getReference();
            if (ref != null) {
                String imported = ref.toString();
                if (imported.startsWith(oldPackage)) {
                    String newImported = imported.replace(oldPackage, newPackage);
                    CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newImported);
                    
                    // Create new import - Spoon should handle static vs regular based on context
                    CtImport newImport = launcher.getFactory().createImport(newTypeRef);
                    imp.replace(newImport);
                    importReplacements++;
                }
            }
        }
        
        System.out.println("Replaced " + importReplacements + " imports");
        
        // Output transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully.");
        System.out.println("All " + oldPackage + " references replaced with " + newPackage);
    }
}