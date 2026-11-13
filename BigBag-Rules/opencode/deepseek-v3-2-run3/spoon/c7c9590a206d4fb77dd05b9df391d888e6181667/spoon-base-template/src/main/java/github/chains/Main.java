package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.visitor.filter.ReferenceTypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration: Define old and new package/class names
    private static final String OLD_FULLY_QUALIFIED_NAME = "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_FULLY_QUALIFIED_NAME = "org.apache.maven.doxia.siterenderer.RenderingContext";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation for package relocation:");
        System.out.println("  From: " + OLD_FULLY_QUALIFIED_NAME);
        System.out.println("  To:   " + NEW_FULLY_QUALIFIED_NAME);
        System.out.println("  Source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Count transformations
        int importUpdates = 0;
        int typeReferenceUpdates = 0;
        int constructorCallUpdates = 0;
        
        // 1. Simple text replacement for imports (Spoon has issues with imports in noClasspath mode)
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        for (Path javaFile : javaFiles) {
            String content = Files.readString(javaFile);
            String oldContent = content;
            
            // Replace import statements
            content = content.replace(
                "import " + OLD_FULLY_QUALIFIED_NAME + ";",
                "import " + NEW_FULLY_QUALIFIED_NAME + ";"
            );
            
            if (!content.equals(oldContent)) {
                Files.writeString(javaFile, content);
                importUpdates++;
            }
        }
        
        // 2. Update type references using Spoon (more reliable for code structure)
        List<CtTypeReference<?>> typeRefs = model.getElements(new ReferenceTypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> typeRef : typeRefs) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.equals(OLD_FULLY_QUALIFIED_NAME)) {
                // Replace the type reference
                CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(NEW_FULLY_QUALIFIED_NAME);
                typeRef.replace(newTypeRef);
                typeReferenceUpdates++;
            }
        }
        
        // 3. Update constructor calls
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            if (constructorCall.getType() != null) {
                String constructorType = constructorCall.getType().getQualifiedName();
                if (constructorType != null && constructorType.equals(OLD_FULLY_QUALIFIED_NAME)) {
                    // Update the constructor call type reference
                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(NEW_FULLY_QUALIFIED_NAME);
                    constructorCall.setType(newTypeRef);
                    constructorCallUpdates++;
                }
            }
        }
        
        // Output results
        System.out.println("\nTransformation completed:");
        System.out.println("  Import statements updated: " + importUpdates);
        System.out.println("  Type references updated: " + typeReferenceUpdates);
        System.out.println("  Constructor calls updated: " + constructorCallUpdates);
        System.out.println("  Total changes: " + (importUpdates + typeReferenceUpdates + constructorCallUpdates));
        
        // Write Spoon changes back (only for non-import changes)
        launcher.prettyprint();
        
        System.out.println("\nOutput written. Please verify the changes.");
    }
}