package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation for javax.validation to jakarta.validation migration.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Transform imports
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class));
        System.out.println("Found " + imports.size() + " imports");
        for (CtImport importDecl : imports) {
            String importStr = importDecl.getReference().toString();
            System.out.println("Import: " + importStr);
            if (importStr.startsWith("javax.validation")) {
                String newImport = importStr.replace("javax.validation", "jakarta.validation");
                importDecl.setReference(launcher.getFactory().createReference(newImport));
                System.out.println("Transformed import: " + importStr + " -> " + newImport);
            }
        }
        
        // Transform type references
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String typeName = typeRef.getQualifiedName();
            if (typeName.startsWith("javax.validation")) {
                String newType = typeName.replace("javax.validation", "jakarta.validation");
                // Create new reference
                CtTypeReference<?> newRef = launcher.getFactory().createReference(newType);
                // Try to replace
                try {
                    typeRef.replace(newRef);
                    System.out.println("Transformed type reference: " + typeName + " -> " + newType);
                } catch (Exception e) {
                    System.err.println("Could not replace type reference: " + typeName);
                }
            }
        }
        
        // Output to same directory
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete.");
    }
}