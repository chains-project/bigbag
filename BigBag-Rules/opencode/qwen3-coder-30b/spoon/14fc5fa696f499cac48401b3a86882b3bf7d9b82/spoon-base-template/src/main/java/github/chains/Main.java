package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Process all Java files in the source directory
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/nem");
        launcher.setSourceOutputDirectory("/workspace/nem");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process the code
        launcher.run();
        
        // Apply transformations
        applyFlywayTransformation(launcher.getFactory());
        
        // Write the modified code back to files
        launcher.process();
    }
    
    public static void applyFlywayTransformation(Factory factory) {
        // Find all Flyway constructor calls with no arguments
        List<CtNewClass> flywayConstructors = factory.getModel().getElements(
            new TypeFilter<>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass element) {
                    if (element.getExecutable() != null && element.getExecutable().getDeclaringType() != null) {
                        return element.getExecutable().getDeclaringType().getQualifiedName().equals("org.flywaydb.core.Flyway") &&
                               element.getArguments().size() == 0;
                    }
                    return false;
                }
            }
        );
        
        // Print how many we found
        System.out.println("Found " + flywayConstructors.size() + " Flyway constructors");
        
        // For now, just report the findings - we'll need a more complete solution
        for (int i = 0; i < Math.min(3, flywayConstructors.size()); i++) {
            CtNewClass flywayConstructor = flywayConstructors.get(i);
            System.out.println("Found constructor at line " + flywayConstructor.getPosition().getLine());
        }
    }
}