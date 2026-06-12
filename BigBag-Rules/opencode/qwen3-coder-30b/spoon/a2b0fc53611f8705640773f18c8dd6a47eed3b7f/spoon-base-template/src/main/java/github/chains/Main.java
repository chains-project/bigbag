package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // This is a generic Spoon transformation for Flyway breaking changes
        // It replaces old Flyway API usage with new API usage
        
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/nem");
        launcher.setSourceOutputDirectory("/workspace/nem-transformed");
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();

        // Find all Flyway constructor calls with no arguments
        List<CtNewClass> flywayConstructors = model.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                return element.getExecutable().getDeclaringType().getQualifiedName().equals("org.flywaydb.core.Flyway") 
                        && element.getArguments().isEmpty();
            }
        });
        
        // Replace old Flyway() constructor with new configuration approach
        for (CtNewClass flywayConstructor : flywayConstructors) {
            // Create new construction: Flyway.configure().load()
            CtInvocation configureInvocation = factory.createInvocation(
                factory.createTypeAccess(factory.Type().createReference("org.flywaydb.core.Flyway")),
                factory.createExecutableReference().setSimpleName("configure")
            );
            
            CtInvocation loadInvocation = factory.createInvocation(configureInvocation, 
                factory.createExecutableReference().setSimpleName("load"));
            
            // Replace the old constructor with the new approach
            flywayConstructor.replace(loadInvocation);
        }
        
        // Find and replace setDataSource calls with dataSource()
        List<CtInvocation> setDataSourceCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setDataSource")
                        && element.getTarget() != null
                        && element.getTarget().getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
            }
        });
        
        for (CtInvocation setDataSourceCall : setDataSourceCalls) {
            // Replace with dataSource() method
            CtInvocation newInvocation = factory.createInvocation(
                setDataSourceCall.getTarget(), 
                factory.createExecutableReference().setSimpleName("dataSource"),
                setDataSourceCall.getArguments()
            );
            setDataSourceCall.replace(newInvocation);
        }
        
        // Find and replace setClassLoader calls with classLoader()
        List<CtInvocation> setClassLoaderCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setClassLoader")
                        && element.getTarget() != null
                        && element.getTarget().getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
            }
        });
        
        for (CtInvocation setClassLoaderCall : setClassLoaderCalls) {
            // Replace with classLoader() method
            CtInvocation newInvocation = factory.createInvocation(
                setClassLoaderCall.getTarget(), 
                factory.createExecutableReference().setSimpleName("classLoader"),
                setClassLoaderCall.getArguments()
            );
            setClassLoaderCall.replace(newInvocation);
        }
        
        // Find and replace setLocations calls with locations()
        List<CtInvocation> setLocationsCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setLocations")
                        && element.getTarget() != null
                        && element.getTarget().getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
            }
        });
        
        for (CtInvocation setLocationsCall : setLocationsCalls) {
            // Replace with locations() method
            CtInvocation newInvocation = factory.createInvocation(
                setLocationsCall.getTarget(), 
                factory.createExecutableReference().setSimpleName("locations"),
                setLocationsCall.getArguments()
            );
            setLocationsCall.replace(newInvocation);
        }
        
        // Find and replace setValidateOnMigrate calls with validateOnMigrate()
        List<CtInvocation> setValidateOnMigrateCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                return element.getExecutable().getSimpleName().equals("setValidateOnMigrate")
                        && element.getTarget() != null
                        && element.getTarget().getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
            }
        });
        
        for (CtInvocation setValidateOnMigrateCall : setValidateOnMigrateCalls) {
            // Replace with validateOnMigrate() method
            CtInvocation newInvocation = factory.createInvocation(
                setValidateOnMigrateCall.getTarget(), 
                factory.createExecutableReference().setSimpleName("validateOnMigrate"),
                setValidateOnMigrateCall.getArguments()
            );
            setValidateOnMigrateCall.replace(newInvocation);
        }
        
        // Process the model to save changes
        launcher.process();
        
        System.out.println("Flyway API transformation completed!");
        System.out.println("Files should now compile without Flyway API errors.");
    }
}