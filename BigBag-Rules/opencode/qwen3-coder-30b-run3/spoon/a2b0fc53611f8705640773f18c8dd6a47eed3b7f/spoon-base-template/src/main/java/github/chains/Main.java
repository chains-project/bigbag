package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/nem");
        launcher.setSourceOutputDirectory("/workspace/nem-transformed");
        
        // Process all Java files
        CtModel model = launcher.buildModel();
        Factory factory = model.getFactory();
        
        // Find all Flyway instantiations that have no arguments (the main problem)
        List<CtNewClass> flywayInstantiations = model.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                if (element.getType().getQualifiedName().equals("org.flywaydb.core.Flyway")) {
                    // Check if there are no arguments in the constructor call
                    return element.getArguments().isEmpty();
                }
                return false;
            }
        });
        
        // Fix the instantiation by wrapping with ClassicConfiguration
        for (CtNewClass flywayInst : flywayInstantiations) {
            // Create ClassicConfiguration instantiation
            CtNewClass configInst = factory.Core().createNewClass();
            configInst.setType(factory.Type().createReference("org.flywaydb.core.api.configuration.ClassicConfiguration"));
            configInst.setArguments(List.of());
            
            // Create the configuration setting calls
            List<CtStatement> configStatements = List.of();
            
            // Create new Flyway constructor call with configuration
            CtNewClass newFlywayInst = factory.Core().createNewClass();
            newFlywayInst.setType(flywayInst.getType());
            newFlywayInst.setArguments(List.of(configInst));
            
            // Replace the old instantiation
            flywayInst.replace(newFlywayInst);
        }
        
        // Add the necessary imports
        CtPackage rootPackage = model.getRootPackage();
        
        // Import for ClassicConfiguration
        CtImport classicConfigImport = factory.Core().createImport();
        classicConfigImport.setReference(factory.Type().createReference("org.flywaydb.core.api.configuration.ClassicConfiguration"));
        rootPackage.getImports().add(classicConfigImport);
        
        // Import for Flyway
        CtImport flywayImport = factory.Core().createImport();
        flywayImport.setReference(factory.Type().createReference("org.flywaydb.core.Flyway"));
        rootPackage.getImports().add(flywayImport);
        
        // Write the transformed code
        launcher.setSourceOutputDirectory("/workspace/nem-transformed");
        launcher.process();
        
        System.out.println("Transformation completed!");
    }
}