package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source-directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_fixed");
        launcher.getEnvironment().setComplianceLevel(11);
        
        // Process the code to fix Flyway constructor calls
        launcher.addProcessor(new FlywayConstructorFixProcessor());
        
        // Run the processing
        launcher.buildModel();
        launcher.process();
        
        System.out.println("Flyway API migration completed.");
    }
}

class FlywayConstructorFixProcessor extends AbstractProcessor<CtNewClass> {
    @Override
    public void process(CtNewClass element) {
        // Check if this is a new Flyway() constructor call
        if (element.getExecutable() != null) {
            CtExecutableReference<?> executable = element.getExecutable();
            if (executable.getDeclaringType() != null && 
                "org.flywaydb.core.Flyway".equals(executable.getDeclaringType().getQualifiedName())) {
                
                // Check if it's the parameterless constructor (no arguments)
                if (element.getArguments().isEmpty()) {
                    // Create the new configuration-based constructor call
                    Factory factory = element.getFactory();
                    
                    // Create the call to Flyway.configure().load()
                    CtInvocation configureCall = factory.createInvocation(
                        factory.createTypeAccess(factory.Type().createReference("org.flywaydb.core.Flyway")),
                        factory.createExecutableReference()
                            .setDeclaringType(factory.Type().createReference("org.flywaydb.core.Flyway"))
                            .setSimpleName("configure"),
                        List.of()
                    );
                    
                    CtInvocation loadCall = factory.createInvocation(
                        configureCall,
                        factory.createExecutableReference()
                            .setDeclaringType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"))
                            .setSimpleName("load"),
                        List.of()
                    );
                    
                    // Replace the constructor call with new Flyway(config)
                    element.setArguments(List.of(loadCall));
                }
            }
        }
    }
}