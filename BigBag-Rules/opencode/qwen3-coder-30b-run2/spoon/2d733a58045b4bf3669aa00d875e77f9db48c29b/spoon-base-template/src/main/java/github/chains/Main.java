package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-folder>");
            System.exit(1);
        }
        
        String sourceFolder = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceFolder);
        launcher.setSourceOutputDirectory(sourceFolder + "-transformed");
        launcher.getEnvironment().setComplianceLevel(11);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find patterns where a new Flyway() is called without arguments
        model.getElements(new TypeFilter<>(CtNewClass.class))
                .stream()
                .filter(nc -> {
                    if (nc.getAnonymousClass() == null || nc.getAnonymousClass().getSuperclass() == null) {
                        return false;
                    }
                    String superclassName = nc.getAnonymousClass().getSuperclass().getSimpleName();
                    return "Flyway".equals(superclassName);
                })
                .forEach(nc -> {
                    // Replace the old instantiation with new configuration-based approach
                    replaceFlywayInstantiation(factory, nc);
                });
                
        // Find all setter calls on Flyway objects
        model.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(invocation -> {
                    if (!(invocation.getTarget() instanceof CtFieldRead)) {
                        return false;
                    }
                    CtFieldRead<?> fieldRead = (CtFieldRead<?>) invocation.getTarget();
                    return invocation.getExecutable().getSimpleName().startsWith("set") && 
                           fieldRead.getVariable().getSimpleName().equals("flyway");
                })
                .forEach(invocation -> {
                    // Transform the setter call
                    transformSetterCall(factory, invocation);
                });
                
        launcher.process();
        System.out.println("Transformation completed. Check " + sourceFolder + "-transformed for results.");
    }
    
    private static void replaceFlywayInstantiation(Factory factory, CtNewClass newClass) {
        // Find the assignment to replace the old Flyway() instantiation
        CtElement parent = newClass.getParent();
        if (parent instanceof CtAssignment) {
            // We're replacing an assignment like: Flyway flyway = new Flyway();
            
            // Create FluentConfiguration.configure() call
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            
            CtInvocation configBuilder = factory.createInvocation(
                factory.createTypeAccess(fluentConfigType),
                factory.createExecutableReference()
                    .setStatic(true)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("configure"),
                List.of()
            );
            
            // Create new Flyway with configuration
            CtNewClass newFlyway = factory.createNewClass();
            newFlyway.setArguments(List.of(configBuilder));
            newFlyway.setAnonymousClass(newClass.getAnonymousClass());
            newFlyway.setType(newClass.getType());
            
            // Replace the old instantiation
            ((CtAssignment<?, ?>) parent).setAssignment(newFlyway);
        }
    }
    
    private static void transformSetterCall(Factory factory, CtInvocation invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        
        // Replace setters with FluentConfiguration calls
        if ("setDataSource".equals(methodName)) {
            // Replace flyway.setDataSource(...) with config.dataSource(...)
            // Create method call: FluentConfiguration.configure().dataSource(...)
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            CtInvocation fluentConfigCall = factory.createInvocation(
                factory.createTypeAccess(fluentConfigType),
                factory.createExecutableReference()
                    .setStatic(true)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("configure"),
                List.of()
            );
            
            // Create the method call: configure().dataSource(...)
            CtInvocation newInvocation = factory.createInvocation(
                fluentConfigCall,
                factory.createExecutableReference()
                    .setStatic(false)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("dataSource"),
                invocation.getArguments()
            );
            
            invocation.replace(newInvocation);
        }
        else if ("setClassLoader".equals(methodName)) {
            // Replace flyway.setClassLoader(...) with config.classLoader(...)
            // Create method call: FluentConfiguration.configure().classLoader(...)
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            CtInvocation fluentConfigCall = factory.createInvocation(
                factory.createTypeAccess(fluentConfigType),
                factory.createExecutableReference()
                    .setStatic(true)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("configure"),
                List.of()
            );
            
            // Create the method call: configure().classLoader(...)
            CtInvocation newInvocation = factory.createInvocation(
                fluentConfigCall,
                factory.createExecutableReference()
                    .setStatic(false)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("classLoader"),
                invocation.getArguments()
            );
            
            invocation.replace(newInvocation);
        }
        else if ("setLocations".equals(methodName)) {
            // Replace flyway.setLocations(...) with config.locations(...)
            // Create method call: FluentConfiguration.configure().locations(...)
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            CtInvocation fluentConfigCall = factory.createInvocation(
                factory.createTypeAccess(fluentConfigType),
                factory.createExecutableReference()
                    .setStatic(true)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("configure"),
                List.of()
            );
            
            // Create the method call: configure().locations(...)
            CtInvocation newInvocation = factory.createInvocation(
                fluentConfigCall,
                factory.createExecutableReference()
                    .setStatic(false)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("locations"),
                invocation.getArguments()
            );
            
            invocation.replace(newInvocation);
        }
        else if ("setValidateOnMigrate".equals(methodName)) {
            // Replace flyway.setValidateOnMigrate(...) with config.validateOnMigrate(...)
            // Create method call: FluentConfiguration.configure().validateOnMigrate(...)
            CtTypeReference<?> fluentConfigType = factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration");
            CtInvocation fluentConfigCall = factory.createInvocation(
                factory.createTypeAccess(fluentConfigType),
                factory.createExecutableReference()
                    .setStatic(true)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("configure"),
                List.of()
            );
            
            // Create the method call: configure().validateOnMigrate(...)
            CtInvocation newInvocation = factory.createInvocation(
                fluentConfigCall,
                factory.createExecutableReference()
                    .setStatic(false)
                    .setDeclaringType(fluentConfigType)
                    .setType(fluentConfigType)
                    .setSimpleName("validateOnMigrate"),
                invocation.getArguments()
            );
            
            invocation.replace(newInvocation);
        }
    }
}