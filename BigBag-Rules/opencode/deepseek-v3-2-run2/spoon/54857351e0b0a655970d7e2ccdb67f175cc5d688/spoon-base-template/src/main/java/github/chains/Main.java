package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java Main <source_directory> <old_fully_qualified_type> <new_fully_qualified_type>");
            System.err.println("Example: java Main /path/to/src net.lingala.zip4j.core.ZipFile net.lingala.zip4j.ZipFile");
            return;
        }
        
        String sourceDir = args[0];
        String oldType = args[1];
        String newType = args[2];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        
        // Run the transformation
        try {
            CtModel model = launcher.buildModel();
            
            // Find all type references to the old type (both qualified and simple)
            List<CtTypeReference<?>> oldTypeRefs = new ArrayList<>();
            for (CtTypeReference<?> ref : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
                if (oldType.equals(ref.getQualifiedName())) {
                    oldTypeRefs.add(ref);
                }
            }
            
            System.out.println("Found " + oldTypeRefs.size() + " type references to " + oldType);
            
            // Replace all type references with simple name references to new type
            // (Spoon will automatically make them fully qualified if needed)
            for (CtTypeReference<?> ref : oldTypeRefs) {
                // Create a reference to the new type
                CtTypeReference<?> newRef = launcher.getFactory().createReference(newType);
                ref.replace(newRef);
            }
            
            // Now find and replace import statements
            // Get all elements and check which are imports
            List<CtImport> oldTypeImports = new ArrayList<>();
            for (CtElement element : model.getElements(new TypeFilter<CtElement>(CtElement.class))) {
                if (element instanceof CtImport) {
                    CtImport imp = (CtImport) element;
                    if (imp.getReference() instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) imp.getReference();
                        if (oldType.equals(typeRef.getQualifiedName())) {
                            oldTypeImports.add(imp);
                        }
                    }
                }
            }
            
            System.out.println("Found " + oldTypeImports.size() + " imports of " + oldType);
            
            // Replace import statements
            for (CtImport imp : oldTypeImports) {
                CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newType);
                CtImport newImport = launcher.getFactory().createImport(newTypeRef);
                imp.replace(newImport);
            }
            
            // Output the transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully. Updated " + oldTypeRefs.size() + " type references and " + oldTypeImports.size() + " imports.");
            System.out.println("Changed: " + oldType + " -> " + newType);
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}