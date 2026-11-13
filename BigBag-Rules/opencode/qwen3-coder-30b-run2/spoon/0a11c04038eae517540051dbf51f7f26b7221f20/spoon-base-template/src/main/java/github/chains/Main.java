package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation tool to fix SnakeYAML 2.0 breaking changes in Constructor calls.
 * 
 * This tool fixes cases where old SnakeYAML Constructor signatures were:
 * new Constructor(Class)
 * 
 * Which should be changed to:
 * new Constructor(Class, new LoaderOptions())
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Set up Spoon
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir + "-fixed");

        // Build model
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        
        // Find all SnakeYAML Constructor calls
        List<CtConstructorCall<?>> constructorCalls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        );

        int fixesApplied = 0;

        for (CtConstructorCall<?> call : constructorCalls) {
            // Check if this is a SnakeYAML Constructor call that needs fixing
            if ("org.yaml.snakeyaml.constructor.Constructor".equals(call.getType().getQualifiedName())) {
                List<CtExpression<?>> arguments = call.getArguments();
                
                // Pattern: new Constructor(Class) - this needs to be fixed to new Constructor(Class, new LoaderOptions())
                if (arguments.size() == 1) {
                    CtExpression<?> firstArg = arguments.get(0);
                    
                    // If first argument is a Class type, this needs fixing
                    if ("java.lang.Class".equals(firstArg.getType().getQualifiedName())) {
                        System.out.println("Found problematic Constructor call: " + call.toString());
                        
                        // Create new LoaderOptions instance
                        CtNewClass newLoaderOptions = factory.Code().createNewClass(
                            factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions"));
                        
                        // Replace with: new Constructor(clazz, new LoaderOptions())
                        call.getArguments().clear();
                        call.getArguments().add(firstArg);  // Add the Class argument
                        call.getArguments().add(newLoaderOptions);  // Add new LoaderOptions() argument
                        
                        fixesApplied++;
                    }
                }
            }
        }

        System.out.println("Applied " + fixesApplied + " fixes to " + sourceDir);
        
        // Output the modified files
        launcher.process();
        System.out.println("Fixed files written to " + sourceDir + "-fixed");
    }
}