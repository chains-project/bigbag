package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        launcher.buildModel();
        
        // Apply transformation: Constructor(Class) -> Constructor(Class, new LoaderOptions())
        launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))
            .stream()
            .filter(constructorCall -> isSnakeYamlConstructorCall(constructorCall))
            .forEach(constructorCall -> transformConstructorCall(constructorCall));
        
        // Output the transformed code
        launcher.setSourceOutputDirectory("./transformed-output");
        launcher.prettyprint();
        
        System.out.println("Transformation completed. Output written to ./transformed-output/");
    }
    
    /**
     * Checks if this constructor call is for org.yaml.snakeyaml.constructor.Constructor
     * with a single Class parameter (the old API pattern)
     */
    private static boolean isSnakeYamlConstructorCall(CtConstructorCall<?> constructorCall) {
        String constructorType = constructorCall.getType() != null ? 
            constructorCall.getType().getQualifiedName() : "";
        
        // Check if it's a snakeyaml Constructor call
        if (!constructorType.equals("org.yaml.snakeyaml.constructor.Constructor")) {
            return false;
        }
        
        // Check if it has exactly one argument
        if (constructorCall.getArguments().size() != 1) {
            return false;
        }
        
        // Check if the single argument is a Class type
        CtExpression<?> arg = constructorCall.getArguments().get(0);
        String argType = arg.getType() != null ? arg.getType().getQualifiedName() : "";
        
        return "java.lang.Class".equals(argType);
    }
    
    /**
     * Transforms a single-parameter Constructor call to use the new two-parameter API
     */
    private static void transformConstructorCall(CtConstructorCall<?> constructorCall) {
        // Get the existing Class argument
        CtExpression<?> classArg = constructorCall.getArguments().get(0);
        
        // Create a new LoaderOptions instance
        CtConstructorCall<?> loaderOptionsCall = constructorCall.getFactory()
            .createConstructorCall(
                constructorCall.getFactory().Type().createReference("org.yaml.snakeyaml.LoaderOptions")
            );
        
        // Replace the constructor call with the new two-parameter version
        constructorCall.getArguments().clear();
        constructorCall.addArgument(classArg);
        constructorCall.addArgument(loaderOptionsCall);
        
        System.out.println("Transformed Constructor call at: " + constructorCall.getPosition());
    }
}