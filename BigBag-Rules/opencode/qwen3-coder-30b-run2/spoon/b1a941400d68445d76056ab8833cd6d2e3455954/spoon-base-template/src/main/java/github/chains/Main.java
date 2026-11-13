package github.chains;

import spoon.reflect.declaration.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.processing.AbstractProcessor;

/**
 * Spoon transformation rule for handling SnakeYAML 2.0 breaking changes.
 * 
 * This transformation specifically addresses constructor signature changes in
 * SnakeYAML 2.0 where all Constructor constructors now require a LoaderOptions parameter.
 * 
 * Breaking Changes in SnakeYAML 2.0:
 * - Old: new Constructor() or new Constructor(Class<?>) 
 * - New: new Constructor(Class<?>, LoaderOptions) or new Constructor(LoaderOptions)
 * 
 * This processor detects SnakeYAML Constructor calls and adds the missing LoaderOptions
 * parameter to maintain compatibility with SnakeYAML 2.0.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("SnakeYAML 2.0 Transformation Rule");
        System.out.println("Generic Spoon transformation for fixing SnakeYAML 2.0 constructor changes");
    }
    
    /**
     * Spoon processor that transforms SnakeYAML constructor calls to be compatible with SnakeYAML 2.0
     * This handles the most common breaking changes in SnakeYAML 2.0 constructor signatures
     * 
     * Key changes handled:
     * - All Constructor constructors now require a LoaderOptions parameter
     * - Transformations: new Constructor() -> new Constructor(new LoaderOptions())
     *                  new Constructor(Class<?>) -> new Constructor(Class<?>, new LoaderOptions())
     */
    public static class SnakeYAML20TransformationProcessor extends AbstractProcessor<CtConstructorCall> {
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall candidate) {
            // Check if this is a constructor call to SnakeYAML's Constructor class
            if (candidate.getType() != null) {
                String typeName = candidate.getType().getQualifiedName();
                return typeName.equals("org.yaml.snakeyaml.constructor.Constructor");
            }
            return false;
        }
        
        @Override
        public void process(CtConstructorCall constructorCall) {
            // Get the factory for creating new expressions
            Factory factory = constructorCall.getFactory();
            
            // Check if the last argument is already a LoaderOptions
            boolean hasLoaderOptions = false;
            if (!constructorCall.getArguments().isEmpty()) {
                var lastArg = constructorCall.getArguments().get(constructorCall.getArguments().size() - 1);
                if (lastArg.getType() != null) {
                    String argTypeName = lastArg.getType().getQualifiedName();
                    hasLoaderOptions = argTypeName.equals("org.yaml.snakeyaml.LoaderOptions");
                }
            }
            
            // If we don't have LoaderOptions as the last argument, we need to add it
            if (!hasLoaderOptions) {
                // Create new LoaderOptions() constructor call
                var loaderOptionsCall = factory.createConstructorCall(
                    factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions")
                );
                
                // Add the LoaderOptions as the last argument
                constructorCall.addArgument(loaderOptionsCall);
            }
        }
    }
}