package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to be transformed
        launcher.addInputResource("/workspace/code-coverage-api-plugin");
        launcher.setSourceOutputDirectory("/workspace/code-coverage-api-plugin-transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all field accesses referring to "by" field
        List<CtFieldAccess> fieldAccesses = model.getElements(new TypeFilter<>(CtFieldAccess.class));
        
        // Transform usages of "by.xpath(...)", "by.id(...)", etc.
        int transformations = 0;
        for (CtFieldAccess fieldAccess : fieldAccesses) {
            if (fieldAccess.getVariable() != null && 
                "by".equals(fieldAccess.getVariable().getSimpleName())) {
                
                // Check if this is a method call like by.xpath(...)
                if (fieldAccess.getParent() instanceof CtInvocation) {
                    CtInvocation invocation = (CtInvocation) fieldAccess.getParent();
                    
                    // Replace by.xpath(...) with By.xpath(...)
                    // Replace by.id(...) with By.id(...)
                    // etc.
                    invocation.setTarget(launcher.getFactory().Code().createTypeAccess(launcher.getFactory().Type().createReference("org.openqa.selenium.By")));
                    transformations++;
                }
            }
        }
        
        // Print a message indicating the transformation is running
        System.out.println("Starting transformation of code-coverage-api-plugin...");
        System.out.println("Made " + transformations + " transformations");
        System.out.println("Transformation completed");
    }
}