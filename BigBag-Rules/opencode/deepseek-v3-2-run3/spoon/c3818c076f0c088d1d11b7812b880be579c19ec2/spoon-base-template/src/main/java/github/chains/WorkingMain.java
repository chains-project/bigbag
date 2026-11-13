package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class WorkingMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create launcher with default settings
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true); // Keep imports
        
        CtModel model = launcher.buildModel();
        
        int transformed = 0;
        
        // Transform imports
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport ctImport : imports) {
            String importStr = ctImport.getReference().toString();
            if (importStr.startsWith("javax.validation")) {
                String newImport = importStr.replace("javax.validation", "jakarta.validation");
                try {
                    ctImport.setReference(launcher.getFactory().createReference(newImport));
                    transformed++;
                    System.out.println("Transformed import: " + importStr + " -> " + newImport);
                } catch (Exception e) {
                    System.err.println("Error transforming import: " + importStr);
                }
            }
        }
        
        // Also transform any type references in the code
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("javax.validation")) {
                String newName = qualifiedName.replace("javax.validation", "jakarta.validation");
                try {
                    // Create new reference and replace
                    CtTypeReference<?> newRef = launcher.getFactory().createReference(newName);
                    typeRef.replace(newRef);
                    transformed++;
                    System.out.println("Transformed reference: " + qualifiedName + " -> " + newName);
                } catch (Exception e) {
                    System.err.println("Error transforming reference: " + qualifiedName);
                }
            }
        }
        
        System.out.println("Total transformations: " + transformed);
        
        // Write output to a different directory to avoid corruption
        String outputDir = sourceDir + "-transformed";
        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output in: " + outputDir);
    }
}