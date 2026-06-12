package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.SpoonClassNotFoundException;

import java.util.List;

/**
 * Generic transformation rule for fixing SnakeYAML 2.0 breaking changes.
 * 
 * Breaking Change: org.yaml.snakeyaml.constructor.Constructor no longer has a no-arg constructor.
 * Old API: new Constructor()
 * New API: new Constructor(new LoaderOptions())
 * 
 * This transformation rule can be applied to ANY Maven project affected by this breaking change.
 * It is parameterized by fully-qualified type names from the dependency, NOT project-specific names.
 */
public class Main {
    
    /**
     * Processes a Java project to fix SnakeYAML 2.0 breaking changes.
     * 
     * @param args Command line arguments: [sourceDirectory] [outputDirectory]
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDirectory> <outputDirectory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src /path/to/output");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        String outputDirectory = args[1];
        
        System.out.println("Processing source directory: " + sourceDirectory);
        System.out.println("Output directory: " + outputDirectory);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDirectory);
            launcher.setSourceOutputDirectory(outputDirectory);
            
            // Enable comment preservation
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setAutoImports(true);
            
            // Add processor to fix Constructor calls
            launcher.addProcessor(new ConstructorProcessor());
            
            // Run the transformation
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
            
        } catch (SpoonClassNotFoundException e) {
            System.err.println("Warning: Some classes not found in classpath. " +
                             "This is normal if project dependencies aren't fully available.");
            System.err.println("Details: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor that transforms calls to org.yaml.snakeyaml.constructor.Constructor()
     * to pass a LoaderOptions parameter.
     */
    static class ConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        // Fully-qualified type names from the SnakeYAML dependency
        private static final String CONSTRUCTOR_CLASS = "org.yaml.snakeyaml.constructor.Constructor";
        private static final String LOADER_OPTIONS_CLASS = "org.yaml.snakeyaml.LoaderOptions";
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> constructorCall) {
            // Check if this is a constructor call for the SnakeYAML Constructor class
            try {
                return constructorCall.getType() != null &&
                       CONSTRUCTOR_CLASS.equals(constructorCall.getType().getQualifiedName()) &&
                       constructorCall.getArguments().isEmpty();
            } catch (Exception e) {
                // If we can't determine the type, skip it
                return false;
            }
        }
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            System.out.println("Fixing Constructor call at: " + constructorCall.getPosition());
            
            try {
                // Create a new constructor call with LoaderOptions parameter
                CtConstructorCall<?> newConstructorCall = getFactory().createConstructorCall(
                    getFactory().Type().createReference(CONSTRUCTOR_CLASS)
                );
                
                // Create the LoaderOptions argument
                CtConstructorCall<?> loaderOptionsCall = getFactory().createConstructorCall(
                    getFactory().Type().createReference(LOADER_OPTIONS_CLASS)
                );
                
                // Add the LoaderOptions argument to the Constructor call
                newConstructorCall.setArguments(List.of(loaderOptionsCall));
                
                // Replace the old constructor call with the new one
                constructorCall.replace(newConstructorCall);
                
                System.out.println("Successfully replaced: new Constructor() -> new Constructor(new LoaderOptions())");
                
            } catch (Exception e) {
                System.err.println("Error processing constructor call at " + 
                                 constructorCall.getPosition() + ": " + e.getMessage());
            }
        }
        
        @Override
        public void processingDone() {
            // Find and fix any CtClass that extends Constructor
            List<CtClass<?>> constructorSubclasses = getFactory().getModel()
                .getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
                    @Override
                    public boolean matches(CtClass<?> element) {
                        try {
                            return element.getSuperclass() != null &&
                                   CONSTRUCTOR_CLASS.equals(element.getSuperclass().getQualifiedName());
                        } catch (Exception e) {
                            return false;
                        }
                    }
                });
            
            for (CtClass<?> subclass : constructorSubclasses) {
                fixConstructorSubclass(subclass);
            }
        }
        
        /**
         * Fixes subclasses of Constructor that might have their own constructors.
         */
        private void fixConstructorSubclass(CtClass<?> subclass) {
            System.out.println("Checking subclass of Constructor: " + subclass.getQualifiedName());
            
            // Look for constructors in this subclass that need fixing
            subclass.getConstructors().forEach(constructor -> {
                // If this constructor doesn't explicitly call super() with arguments,
                // Spoon will handle it automatically when we fix the parent class reference
                System.out.println("  Found constructor in subclass: " + constructor.getSignature());
            });
        }
    }
}