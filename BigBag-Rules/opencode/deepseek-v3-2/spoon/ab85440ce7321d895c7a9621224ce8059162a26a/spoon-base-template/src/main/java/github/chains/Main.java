package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation to fix constructor API breaking changes.
 * 
 * This transformation removes the first parameter from constructor calls
 * of specified classes, which handles the case where a new API added a
 * parameter that was later removed (or vice versa).
 * 
 * Example: StringContains(boolean, String) -> StringContains(String)
 */
public class Main {
    
    /**
     * Processor that transforms constructor calls by removing the first parameter.
     */
    public static class RemoveFirstParameterProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        
        private final String targetClass;
        
        /**
         * Creates a processor for a specific class.
         * 
         * @param targetClass Fully qualified class name
         */
        public RemoveFirstParameterProcessor(String targetClass) {
            this.targetClass = targetClass;
        }
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            // Check if this is a constructor call for our target class
            if (constructorCall.getType() != null && 
                constructorCall.getType().getQualifiedName().equals(targetClass)) {
                
                // Check if it has at least 2 arguments (we need to remove first)
                if (constructorCall.getArguments().size() >= 2) {
                    // Get all arguments except the first one
                    List<Object> remainingArgs = new ArrayList<>();
                    for (int i = 1; i < constructorCall.getArguments().size(); i++) {
                        remainingArgs.add(constructorCall.getArguments().get(i));
                    }
                    
                    // Create a new constructor call with remaining arguments
                    CtConstructorCall<?> newCall = getFactory().createConstructorCall(
                        getFactory().Type().createReference(targetClass)
                    );
                    
                    // Add all arguments except the first
                    for (Object arg : remainingArgs) {
                        newCall.addArgument((spoon.reflect.code.CtExpression<?>) arg);
                    }
                    
                    // Replace the old constructor call with the new one
                    constructorCall.replace(newCall);
                    
                    System.out.println("Transformed " + targetClass + " constructor call at " + 
                        constructorCall.getPosition().toString() + 
                        " (removed first parameter)");
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            if (candidate.getType() == null) {
                return false;
            }
            String className = candidate.getType().getQualifiedName();
            return className.equals(targetClass) && candidate.getArguments().size() >= 2;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transform.jar <sourceDir> <outputDir>");
            System.err.println("  sourceDir: Directory containing Java source files to transform");
            System.err.println("  outputDir: Directory where transformed files will be written");
            System.err.println("");
            System.err.println("Example for Hamcrest API fix:");
            System.err.println("  This transformation removes the first boolean parameter from");
            System.err.println("  StringContains and StringStartsWith constructor calls:");
            System.err.println("  StringContains(boolean, String) -> StringContains(String)");
            System.err.println("  StringStartsWith(boolean, String) -> StringStartsWith(String)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying constructor API fix transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add processors for Hamcrest classes (these are the specific classes with issues)
        // Note: In a more generic solution, these would be parameters
        launcher.addProcessor(new RemoveFirstParameterProcessor(
            "org.hamcrest.core.StringContains"));
        launcher.addProcessor(new RemoveFirstParameterProcessor(
            "org.hamcrest.core.StringStartsWith"));
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputDir);
        
        // Run the transformation
        launcher.run();
        
        System.out.println("Transformation complete!");
        System.out.println("Transformed files written to: " + outputDir);
    }
}