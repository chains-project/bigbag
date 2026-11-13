package github.chains;

import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class Main {
    
    // Mapping from old setter methods to new fluent configuration methods
    private static final Map<String, String> SETTER_TO_FLUENT_MAP = new HashMap<>();
    
    static {
        SETTER_TO_FLUENT_MAP.put("setDataSource", "dataSource");
        SETTER_TO_FLUENT_MAP.put("setClassLoader", "classLoader");
        SETTER_TO_FLUENT_MAP.put("setLocations", "locations");
        SETTER_TO_FLUENT_MAP.put("setValidateOnMigrate", "validateOnMigrate");
        // Add other setters as needed
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        
        // Build the model
        launcher.buildModel();
        
        Factory factory = launcher.getFactory();
        
        // Find all new Flyway() constructor calls
        List<CtNewClass<?>> flywayConstructors = factory.getModel().getRootPackage()
            .filterChildren(new TypeFilter<CtNewClass<?>>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass<?> newClass) {
                    try {
                        CtTypeReference<?> typeRef = newClass.getType();
                        if (typeRef != null && "org.flywaydb.core.Flyway".equals(typeRef.getQualifiedName())) {
                            return true;
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                    return false;
                }
            }).list();
        
        System.out.println("Found " + flywayConstructors.size() + " Flyway constructor calls");
        
        for (CtNewClass<?> constructor : flywayConstructors) {
            try {
                transformFlywayConstructor(constructor, factory);
            } catch (Exception e) {
                System.err.println("Error transforming constructor: " + e.getMessage());
            }
        }
        
        // Output transformed code
        System.out.println("Applying transformations...");
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFlywayConstructor(CtNewClass<?> constructor, Factory factory) {
        System.out.println("Transforming Flyway constructor at: " + constructor.getPosition());
        
        // Get the existing type reference from the constructor
        CtTypeReference<?> flywayType = constructor.getType();
        
        // Create the replacement: Flyway.configure()
        CtInvocation<?> configureCall = factory.Code().createInvocation(
            factory.Code().createTypeAccess(flywayType),
            factory.createExecutableReference().setSimpleName("configure")
        );
        
        // Replace the constructor with configure() call
        constructor.replace(configureCall);
        
        System.out.println("Replaced new Flyway() with Flyway.configure()");
        
        // Note: This is a simplified transformation that only replaces the constructor.
        // A complete transformation would also need to:
        // 1. Find the variable assignment
        // 2. Transform setter calls to fluent configuration calls  
        // 3. Add .load() at the end
        // This shows the core pattern for a generic transformation rule
    }
}