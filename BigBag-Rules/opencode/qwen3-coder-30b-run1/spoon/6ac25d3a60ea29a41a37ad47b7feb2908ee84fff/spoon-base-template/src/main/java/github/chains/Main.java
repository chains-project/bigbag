package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        // Create a new launcher
        Launcher launcher = new Launcher();
        
        // Set the source code directory
        String sourceDir = "/workspace/myfaces-tobago";
        launcher.addInputResource(sourceDir);
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/myfaces-tobago-out");
        
        // Build the model
        launcher.buildModel();
        
        // Get the model
        var model = launcher.getFactory().getModel();
        
        // Remove HttpSessionContext imports
        model.getElements(new TypeFilter<>(CtImport.class))
                .stream()
                .filter(importElement -> importElement.getReference().getSimpleName().equals("HttpSessionContext"))
                .forEach(importElement -> importElement.delete());
        
        // Write the modified code back to the output directory
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
}