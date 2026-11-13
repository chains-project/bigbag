package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory> <output-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }

        String sourcePath = args[0];
        String outputPath = args[1];
        
        System.out.println("Transforming MVC imports from javax.mvc to jakarta.mvc");
        System.out.println("Source directory: " + sourcePath);
        System.out.println("Output directory: " + outputPath);

        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.addInputResource(sourcePath);
        launcher.setSourceOutputDirectory(outputPath);
        
        // Get environment and configure it
        launcher.getEnvironment().setNoClasspath(true); // Don't require classpath for transformation
        launcher.getEnvironment().setAutoImports(true); // Keep auto-imports enabled
        
        // Build model
        CtModel model = launcher.buildModel();
        
        int importCount = 0;
        int referenceCount = 0;
        
        // Get all compilation units (files)
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<>(CtCompilationUnit.class));
        
        for (CtCompilationUnit cu : compilationUnits) {
            // Process imports
            for (CtImport imp : cu.getImports()) {
                String importStr = imp.toString();
                if (importStr.startsWith("import javax.mvc")) {
                    // Get the reference from the import
                    spoon.reflect.reference.CtReference ref = imp.getReference();
                    if (ref instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
                        String qualifiedName = typeRef.getQualifiedName();
                        if (qualifiedName.startsWith("javax.mvc.")) {
                            // Create new qualified name
                            String newQualifiedName = qualifiedName.replace("javax.mvc.", "jakarta.mvc.");
                            // Create new type reference
                            CtTypeReference<?> newTypeRef = launcher.getFactory().createReference(newQualifiedName);
                            // Update the import's reference
                            imp.setReference(newTypeRef);
                            importCount++;
                            System.out.println("Transformed import: " + qualifiedName + " -> " + newQualifiedName);
                        }
                    }
                }
            }
        }
        
        // Also update any fully qualified type references in the code
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {});
        for (CtTypeReference<?> ref : typeRefs) {
            String qualifiedName = ref.getQualifiedName();
            if (qualifiedName.startsWith("javax.mvc.")) {
                String newQualifiedName = qualifiedName.replace("javax.mvc.", "jakarta.mvc.");
                // Create new reference and replace
                CtTypeReference<?> newRef = launcher.getFactory().createReference(newQualifiedName);
                ref.replace(newRef);
                referenceCount++;
                System.out.println("Transformed type reference: " + qualifiedName + " -> " + newQualifiedName);
            }
        }
        
        // Write transformed code to output directory
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Updated " + importCount + " imports and " + referenceCount + " type references.");
    }
}