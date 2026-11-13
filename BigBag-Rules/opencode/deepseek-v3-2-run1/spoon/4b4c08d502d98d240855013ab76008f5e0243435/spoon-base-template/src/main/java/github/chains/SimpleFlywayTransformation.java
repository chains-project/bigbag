package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

public class SimpleFlywayTransformation {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-base-1.0-SNAPSHOT.jar github.chains.SimpleFlywayTransformation <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying simplified Flyway API migration to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all constructor calls to Flyway
        List<CtConstructorCall> flywayCtors = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                try {
                    return element.getType() != null && 
                           element.getType().getQualifiedName().equals("org.flywaydb.core.Flyway");
                } catch (Exception e) {
                    return false;
                }
            }
        });
        
        System.out.println("Found " + flywayCtors.size() + " Flyway constructor calls");
        
        for (CtConstructorCall ctorCall : flywayCtors) {
            System.out.println("Processing Flyway constructor at line: " + 
                             ctorCall.getPosition().getLine());
            
            // Create the replacement: Flyway.configure().load()
            CtInvocation configureCall = launcher.getFactory().createInvocation(
                launcher.getFactory().createTypeAccess(
                    launcher.getFactory().Type().get("org.flywaydb.core.Flyway").getReference()
                ),
                launcher.getFactory().createExecutableReference().setSimpleName("configure")
            );
            
            CtInvocation loadCall = launcher.getFactory().createInvocation(
                configureCall,
                launcher.getFactory().createExecutableReference().setSimpleName("load")
            );
            
            // Replace the constructor call
            ctorCall.replace(loadCall);
            
            // Now we need to find and fix any setter methods that follow this constructor
            fixSetterMethods(ctorCall, launcher);
        }
        
        // Save transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete! You may need to manually adjust setter methods to fluent API.");
    }
    
    private static void fixSetterMethods(CtConstructorCall originalCtor, Launcher launcher) {
        // Get the parent method or block
        CtMethod method = originalCtor.getParent(CtMethod.class);
        if (method == null) {
            System.out.println("  Warning: Could not find parent method");
            return;
        }
        
        CtBlock body = method.getBody();
        if (body == null) {
            return;
        }
        
        // Look for setter calls and print warnings/advice
        List<CtInvocation> invocations = body.getElements(new TypeFilter<CtInvocation>(CtInvocation.class));
        for (CtInvocation invoc : invocations) {
            String methodName = invoc.getExecutable().getSimpleName();
            if (methodName.startsWith("set")) {
                System.out.println("  Found setter call: " + methodName + " at line " + 
                                 invoc.getPosition().getLine());
                System.out.println("  You need to manually change: " + methodName + 
                                 " -> " + mapSetterToFluent(methodName));
                System.out.println("  And chain it before .load()");
            }
        }
    }
    
    private static String mapSetterToFluent(String setterName) {
        switch (setterName) {
            case "setDataSource":
                return "dataSource";
            case "setClassLoader":
                return "classLoader";
            case "setLocations":
                return "locations";
            case "setValidateOnMigrate":
                return "validateOnMigrate";
            default:
                return setterName.substring(3, 4).toLowerCase() + setterName.substring(4);
        }
    }
}