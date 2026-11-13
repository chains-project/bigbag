package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;

public class TestTransformation {
    
    public static void main(String[] args) {
        // Create a simple test to validate the setup works
        System.out.println("Testing Spoon transformation setup...");
        
        // Create a minimal test launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(17);
        
        // Add the test file
        launcher.addInputResource("src/test/java/github/chains/TestTranslateV2.java");
        
        // Try to build model
        try {
            CtModel model = launcher.buildModel();
            System.out.println("Model built successfully with " + 
                model.getAllTypes().size() + " types");
                
            // Count invocations
            int invocationCount = model.getElements(new TypeFilter<>(CtInvocation.class)).size();
            System.out.println("Found " + invocationCount + " method invocations");
            
            // Show some classes
            for (CtClass<?> clazz : model.getElements(new TypeFilter<>(CtClass.class))) {
                System.out.println("Found class: " + clazz.getQualifiedName());
            }
            
        } catch (Exception e) {
            System.err.println("Error building model: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("Test completed.");
    }
}