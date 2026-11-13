package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create a new Launcher instance
        Launcher launcher = new Launcher();
        
        // Set the source code directory
        launcher.addInputResource("/workspace/nem");
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/workspace/nem-transformed");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply the Flyway transformation
        applyFlywayTransformation(model);
        
        // Print the number of transformations applied
        System.out.println("Flyway transformation completed");
    }
    
    private static void applyFlywayTransformation(CtModel model) {
        Factory factory = model.getFactory();
        
        // Find all Flyway constructor calls that use no arguments
        List<CtNewClass> flywayConstructors = model.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                return "org.flywaydb.core.Flyway".equals(element.getType().getQualifiedName()) &&
                       element.getArguments().isEmpty();
            }
        });
        
        // Transform the constructors
        for (CtNewClass flywayConstructor : flywayConstructors) {
            // Replace it with: Flyway.configure(classLoader).dataSource().locations().validateOnMigrate().load()
            // Get the class loader from the enclosing method or class
            // For simplicity, we'll use the class that contains the Flyway instantiation
            // In a real implementation, we'd need to extract this dynamically
            
            // Create the replacement code: Flyway.configure(classLoader).dataSource().locations().validateOnMigrate().load()
            CtInvocation configureInvocation = factory.createInvocation(
                factory.createTypeAccess(factory.Type().createReference("org.flywaydb.core.Flyway")),
                factory.createExecutableReference()
                    .setSimpleName("configure")
            );
            
            // Add the classloader argument (we'll use the class that contains the Flyway instantiation)
            // This is a simplification - in practice, we'd need to determine the correct classloader
            // For now, we'll use a placeholder that will be replaced by the actual class
            CtInvocation classLoaderInvocation = factory.createInvocation(
                configureInvocation,
                factory.createExecutableReference()
                    .setSimpleName("configure")
            );
            
            // Create the full chain: Flyway.configure().dataSource().locations().validateOnMigrate().load()
            CtInvocation dataSourceInvocation = factory.createInvocation(
                configureInvocation,
                factory.createExecutableReference()
                    .setSimpleName("dataSource")
            );
            
            CtInvocation locationsInvocation = factory.createInvocation(
                dataSourceInvocation,
                factory.createExecutableReference()
                    .setSimpleName("locations")
            );
            
            CtInvocation validateOnMigrateInvocation = factory.createInvocation(
                locationsInvocation,
                factory.createExecutableReference()
                    .setSimpleName("validateOnMigrate")
            );
            
            CtInvocation loadInvocation = factory.createInvocation(
                validateOnMigrateInvocation,
                factory.createExecutableReference()
                    .setSimpleName("load")
            );
            
            // Replace the constructor with the new invocation
            flywayConstructor.replace(loadInvocation);
        }
        
        // Find all method calls that use the old setter methods
        List<CtInvocation> methodCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable() != null && 
                       (element.getExecutable().getSimpleName().equals("setDataSource") ||
                        element.getExecutable().getSimpleName().equals("setLocations") ||
                        element.getExecutable().getSimpleName().equals("setValidateOnMigrate"));
            }
        });
        
        // Transform the method calls to use fluent API
        for (CtInvocation methodCall : methodCalls) {
            // Get the method name
            String methodName = methodCall.getExecutable().getSimpleName();
            
            // Get the receiver (the flyway instance)
            CtExpression<?> receiver = methodCall.getTarget();
            
            // Create a new fluent API call
            CtInvocation newInvocation;
            
            switch (methodName) {
                case "setDataSource":
                    // Change from flyway.setDataSource(dataSource) to flyway.dataSource(dataSource)
                    newInvocation = factory.createInvocation(
                        receiver,
                        factory.createExecutableReference()
                            .setSimpleName("dataSource")
                    );
                    break;
                case "setLocations":
                    // Change from flyway.setLocations(locations) to flyway.locations(locations)
                    newInvocation = factory.createInvocation(
                        receiver,
                        factory.createExecutableReference()
                            .setSimpleName("locations")
                    );
                    break;
                case "setValidateOnMigrate":
                    // Change from flyway.setValidateOnMigrate(validate) to flyway.validateOnMigrate(validate)
                    newInvocation = factory.createInvocation(
                        receiver,
                        factory.createExecutableReference()
                            .setSimpleName("validateOnMigrate")
                    );
                    break;
                default:
                    continue; // Skip unrecognized methods
            }
            
            // Add the arguments from the old call
            List<CtExpression<?>> arguments = methodCall.getArguments();
            for (CtExpression<?> arg : arguments) {
                newInvocation.addArgument(arg);
            }
            
            // Replace the old call with the new one
            methodCall.replace(newInvocation);
        }
    }
}