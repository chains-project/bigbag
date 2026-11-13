package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-dir> <output-dir>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src /path/to/output");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying Struts 2.5.26 migration transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true); // Enable auto-imports
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all compilation units to process imports
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class) {
            @Override
            public boolean matches(CtCompilationUnit cu) {
                return true; // Match all compilation units
            }
        });
        
        int importUpdates = 0;
        int typeRefUpdates = 0;
        
        for (CtCompilationUnit cu : compilationUnits) {
            // Process imports in this compilation unit
            for (CtImport ctImport : cu.getImports()) {
                String importStr = ctImport.toString();
                if (importStr.contains("org.apache.struts2.dispatcher.ng")) {
                    String newImportStr = importStr.replace("org.apache.struts2.dispatcher.ng", "org.apache.struts2.dispatcher");
                    System.out.println("Will update import: " + importStr + " -> " + newImportStr);
                    importUpdates++;
                    // We'll handle this by removing the old import - auto-import will add the correct one
                    // when it sees the updated type references
                    ctImport.delete();
                }
            }
        }
        
        // Find and fix all type references
        List<CtTypeReference<?>> typeRefs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                String qualifiedName = typeRef.getQualifiedName();
                return qualifiedName != null && qualifiedName.startsWith("org.apache.struts2.dispatcher.ng");
            }
        });
        
        for (CtTypeReference<?> typeRef : typeRefs) {
            String oldName = typeRef.getQualifiedName();
            String newName = oldName.replace("org.apache.struts2.dispatcher.ng", "org.apache.struts2.dispatcher");
            System.out.println("Updating type reference: " + oldName + " -> " + newName);
            
            // Create new type reference
            CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(newName);
            
            // Replace the type reference in the AST
            typeRef.replace(newTypeRef);
            typeRefUpdates++;
        }
        
        // Write transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation complete! Updated " + typeRefUpdates + " type references and marked " + importUpdates + " imports for update.");
        System.out.println("Note: Old imports were removed. Auto-import will add correct imports when needed.");
    }
}