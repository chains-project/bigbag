package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

public class Main {
    
    static class SnakeYaml20MigrationProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
            // Check if this is a constructor call for org.yaml.snakeyaml.constructor.Constructor
            CtTypeReference<?> constructedType = constructorCall.getType();
            if (constructedType == null) {
                return false;
            }
            
            String typeName = constructedType.getQualifiedName();
            if (!"org.yaml.snakeyaml.constructor.Constructor".equals(typeName)) {
                return false;
            }
            
            // Check if it has exactly 1 argument (the old API)
            List<?> arguments = constructorCall.getArguments();
            return arguments != null && arguments.size() == 1;
        }
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            System.out.println("Found snakeyaml Constructor call to migrate: " + constructorCall.getPosition());
            
            // Create the new constructor call with LoaderOptions
            CtTypeReference<?> loaderOptionsType = getFactory().Type().createReference("org.yaml.snakeyaml.LoaderOptions");
            CtConstructorCall<?> loaderOptionsConstructor = getFactory().Code().createConstructorCall(loaderOptionsType);
            
            // Keep the existing class argument and add LoaderOptions as second argument
            CtConstructorCall<?> newConstructorCall = getFactory().Code().createConstructorCall(
                constructorCall.getType(),
                constructorCall.getArguments().get(0),
                loaderOptionsConstructor
            );
            
            // Replace the old constructor call with the new one
            constructorCall.replace(newConstructorCall);
            
            // Note: User may need to add import for LoaderOptions manually
            // import org.yaml.snakeyaml.LoaderOptions;
            
            System.out.println("Migrated constructor call at: " + constructorCall.getPosition());
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Output will be written to 'spooned' subdirectory");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying snakeyaml 2.0 migration to: " + sourceDir);
        System.out.println("This transformation fixes: new Constructor(SomeClass.class) -> new Constructor(SomeClass.class, new LoaderOptions())");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory("spooned");
        
        // Create the transformation
        launcher.addProcessor(new SnakeYaml20MigrationProcessor());
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Transformation complete. Output written to: spooned/ directory");
        System.out.println("Summary: Applied snakeyaml 2.0 migration to fix Constructor API breaking change");
    }
}