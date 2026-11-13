package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Generic transformation to fix jasypt breaking changes in Maven projects.
 * This rule addresses the removal of org.jasypt.spring.security package and classes.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar <jar-file> <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "/generated");
        
        // Process the model
        CtModel model = launcher.buildModel();
        
        // Fix imports for jasypt package changes
        fixJasyptImports(model);
        
        // Save the transformed code
        launcher.setSourceOutputDirectory(sourceDirectory + "/generated");
        launcher.process();
        
        System.out.println("Jasypt API transformation completed successfully.");
    }
    
    /**
     * Fix jasypt imports that have been moved or removed
     */
    private static void fixJasyptImports(CtModel model) {
        // Remove old jasypt spring security imports
        model.getElements(new TypeFilter<>(CtImport.class))
            .stream()
            .filter(importStmt -> importStmt.getReference().toString().startsWith("org.jasypt.spring.security"))
            .forEach(importStmt -> importStmt.delete());
    }
}