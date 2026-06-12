package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;

import java.io.File;

/**
 * Main class to demonstrate usage of the ScriptResultFixTransformation
 */
public class Main {
    public static void main(String[] args) {
        // Create a launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to process
        launcher.addInputResource("/workspace/code-coverage-api-plugin/ui-tests/src/main/java");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/code-coverage-api-plugin/ui-tests/src/main/java");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Apply our transformation to all classes
        for (CtType<?> type : model.getAllTypes()) {
            if (type instanceof CtClass) {
                CtClass<?> clazz = (CtClass<?>) type;
                // Apply the ScriptResultFixTransformation  
                ScriptResultFixTransformation.apply(clazz);
            }
        }
        
        // Print a success message
        System.out.println("ScriptResultFixTransformation analysis completed!");
    }
}