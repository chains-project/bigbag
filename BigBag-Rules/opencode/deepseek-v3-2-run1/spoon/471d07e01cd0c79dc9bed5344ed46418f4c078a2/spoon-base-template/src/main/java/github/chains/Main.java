package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory> <output-directory>");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/src /path/to/transformed");
            System.exit(1);
        }

        String sourcePath = args[0];
        String outputPath = args[1];
        
        System.out.println("Transforming source directory: " + sourcePath);
        System.out.println("Output directory: " + outputPath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory(outputPath);
        
        // Enable comment preservation
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Counter for changes
        int importChanges = 0;
        int typeRefChanges = 0;
        
        // Transform imports from v1beta1 to v3
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            String importStr = ctImport.toString();
            if (importStr.contains("com.google.api.services.cloudresourcemanager.") && 
                !importStr.contains("com.google.api.services.cloudresourcemanager.v3.")) {
                
                // Update import to v3
                String newImport = importStr.replace(
                    "com.google.api.services.cloudresourcemanager.",
                    "com.google.api.services.cloudresourcemanager.v3.");
                
                // Create new import reference
                CtTypeReference<?> newRef = launcher.getFactory().createReference(
                    newImport.replace("import ", "").replace(";", "").trim());
                ctImport.setReference(newRef);
                importChanges++;
                System.out.println("Updated import: " + importStr + " -> " + newImport);
            }
        }
        
        // Transform type references in code (fully-qualified class names)
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName.startsWith("com.google.api.services.cloudresourcemanager.") && 
                !qualifiedName.startsWith("com.google.api.services.cloudresourcemanager.v3.")) {
                
                // Update to v3
                String newQualifiedName = qualifiedName.replace(
                    "com.google.api.services.cloudresourcemanager.",
                    "com.google.api.services.cloudresourcemanager.v3.");
                
                // Create new type reference
                CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newQualifiedName);
                typeRef.replace(newTypeRef);
                typeRefChanges++;
                System.out.println("Updated type reference: " + qualifiedName + " -> " + newQualifiedName);
            }
        }
        
        System.out.println("\nTransformation summary:");
        System.out.println("  Import statements updated: " + importChanges);
        System.out.println("  Type references updated: " + typeRefChanges);
        System.out.println("  Total changes: " + (importChanges + typeRefChanges));
        
        // Write transformed code to output directory
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete. Output written to: " + outputPath);
        
        // Warn about potential issues
        if (importChanges + typeRefChanges == 0) {
            System.out.println("Warning: No changes were made. Check if the source uses the old v1beta1 API.");
        }
        
        // Note about model class changes
        System.out.println("\nNote: This transformation only handles package renaming.");
        System.out.println("Some model classes may not exist in v3 (e.g., BooleanPolicy, OrgPolicy, etc.).");
        System.out.println("Additional manual fixes may be required for removed classes.");
    }
}