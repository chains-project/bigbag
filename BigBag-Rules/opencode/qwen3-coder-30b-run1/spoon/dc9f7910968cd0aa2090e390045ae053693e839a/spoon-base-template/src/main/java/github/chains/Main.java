package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        // Create a launcher for Spoon
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        
        // Add the source directory to process
        launcher.addInputResource("/workspace/nem");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Apply our Flyway fix visitor to all classes
        FlywayFixVisitor visitor = new FlywayFixVisitor(launcher.getFactory());
        
        // Get all classes in the model
        for (CtClass<?> clazz : model.getElements(new TypeFilter<>(CtClass.class))) {
            clazz.accept(visitor);
        }
        
        // Write back the modified code
        launcher.setSourceOutputDirectory("/workspace/nem_fixed");
        launcher.process();
        
        System.out.println("Flyway API fix applied successfully!");
    }
}