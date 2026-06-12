package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-template.jar github.chains.Main <source directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        // Build the model
        launcher.buildModel();
        
        // Get the Spoon model
        var model = launcher.getFactory().getModel();
        
        // Transformations for the validation API migration
        // Remove javax.validation imports
        removeValidationImports(launcher.getFactory());
        
        // Write the transformed code back
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.process();
        
        System.out.println("Transformation completed successfully");
    }
    
    private static void removeValidationImports(spoon.reflect.factory.Factory factory) {
        // Find all imports that reference javax.validation or org.apache.commons.beanutils
        List<CtImport> importsToRemove = new ArrayList<>();
        for (CtImport imp : factory.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            String importName = imp.getReference().toString();
            if (importName.startsWith("javax.validation") || 
                importName.startsWith("org.apache.commons.beanutils")) {
                importsToRemove.add(imp);
            }
        }
        
        // Remove the imports
        for (CtImport imp : importsToRemove) {
            imp.delete();
        }
    }
}
