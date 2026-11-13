package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("This transformation fixes zip4j 2.x breaking change:");
            System.err.println("  Old: net.lingala.zip4j.core.ZipFile");
            System.err.println("  New: net.lingala.zip4j.ZipFile");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        
        // Configure environment
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            // Build model
            CtModel model = launcher.buildModel();
            
            // Counter for changes
            int importChanges = 0;
            int typeRefChanges = 0;
            
            // Find all imports
            List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class));
            for (CtImport ctImport : imports) {
                String importStr = ctImport.toString();
                
                // Check if this is an import of the old zip4j.core.ZipFile
                if (importStr.contains("net.lingala.zip4j.core.ZipFile")) {
                    System.out.println("Found old import: " + importStr);
                    
                    // Create new import reference
                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference("net.lingala.zip4j.ZipFile");
                    
                    // Replace the import
                    ctImport.setReference(newTypeRef);
                    
                    importChanges++;
                    System.out.println("Replaced import with: net.lingala.zip4j.ZipFile");
                }
            }
            
            // Find all type references in the code
            List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                
                // Check for fully-qualified references to old zip4j.core.ZipFile
                if ("net.lingala.zip4j.core.ZipFile".equals(qualifiedName)) {
                    System.out.println("Found fully-qualified reference to: " + qualifiedName);
                    
                    // Create new type reference
                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference("net.lingala.zip4j.ZipFile");
                    
                    // Replace the type reference
                    typeRef.replace(newTypeRef);
                    
                    typeRefChanges++;
                    System.out.println("Replaced type reference with: net.lingala.zip4j.ZipFile");
                }
            }
            
            System.out.println("\nTransformation complete!");
            System.out.println("Updated " + importChanges + " import statements");
            System.out.println("Updated " + typeRefChanges + " type references");
            
            // Write transformed code back
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}