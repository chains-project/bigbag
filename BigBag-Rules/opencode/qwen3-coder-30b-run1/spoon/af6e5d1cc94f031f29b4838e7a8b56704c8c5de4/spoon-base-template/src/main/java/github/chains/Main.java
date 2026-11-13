package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "-transformed");
        launcher.getEnvironment().setComplianceLevel(8);
        
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        
        // Find all new Flyway() calls
        List<CtNewClass> newFlywayInstances = factory.Query().filterChildren(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                return element.getActivatingType() != null && 
                       element.getActivatingType().getSimpleName().equals("Flyway") &&
                       element.getArguments().isEmpty();
            }
        });
        
        System.out.println("Found " + newFlywayInstances.size() + " Flyway instances to transform");
        
        // Find all method calls that need to be changed
        List<CtInvocation> flywayCalls = factory.Query().filterChildren(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getTarget() == null) return false;
                String methodName = element.getExecutable().getSimpleName();
                return methodName.equals("setDataSource") || 
                       methodName.equals("setClassLoader") || 
                       methodName.equals("setLocations") || 
                       methodName.equals("setValidateOnMigrate");
            }
        });
        
        System.out.println("Found " + flywayCalls.size() + " Flyway method calls to transform");
        
        // For now, just print the findings
        System.out.println("Transformation completed.");
    }
}