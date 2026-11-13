package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation for fixing SnakeYAML 2.0 breaking API changes.
 * 
 * Fixes: Constructor(Class) -> Constructor(Class, LoaderOptions)
 * 
 * This transformation searches for all constructor calls to 
 * org.yaml.snakeyaml.constructor.Constructor with exactly one argument
 * of type java.lang.Class, and adds a second argument: new LoaderOptions().
 * 
 * Usage: java -jar spoon-base.jar <source_directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base.jar <source_directory>");
            System.err.println("Example: java -jar spoon-base.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: Constructor(Class) -> Constructor(Class, LoaderOptions)");
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            
            // Build the model
            launcher.buildModel();
            
            final Factory factory = launcher.getFactory();
            int transformationCount = 0;
            
            // Find all constructor calls
            for (CtConstructorCall<?> constructorCall : 
                 launcher.getModel().getRootPackage().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))) {
                
                try {
                    // Check if this is a Constructor call from org.yaml.snakeyaml.constructor
                    CtTypeReference<?> targetTypeRef = constructorCall.getType();
                    if (targetTypeRef != null) {
                        String qualifiedName = targetTypeRef.getQualifiedName();
                        if (qualifiedName != null && qualifiedName.equals("org.yaml.snakeyaml.constructor.Constructor")) {
                            
                            // Check if it has exactly one argument
                            if (constructorCall.getArguments().size() == 1) {
                                // Check if the single argument is a Class type
                                CtTypeReference<?> argType = constructorCall.getArguments().get(0).getType();
                                if (argType != null && argType.getQualifiedName().equals("java.lang.Class")) {
                                    
                                    System.out.println("Found Constructor(Class) call at: " + 
                                        constructorCall.getPosition().getFile().getName() + ":" + 
                                        constructorCall.getPosition().getLine());
                                    
                                    // Add LoaderOptions as second argument
                                    try {
                                        CtTypeReference<?> loaderOptionsType = factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions");
                                        CtConstructorCall<?> loaderOptionsCall = factory.createConstructorCall(loaderOptionsType);
                                        
                                        // Add the new argument
                                        constructorCall.addArgument(loaderOptionsCall);
                                        System.out.println("  -> Transformed to Constructor(Class, LoaderOptions)");
                                        transformationCount++;
                                    } catch (Exception e) {
                                        System.err.println("Error creating LoaderOptions constructor call: " + e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing constructor call: " + e.getMessage());
                }
            }
            
            if (transformationCount > 0) {
                // Output transformed code
                String outputDir = "/tmp/spoon-output";
                launcher.setSourceOutputDirectory(outputDir);
                launcher.prettyprint();
                
                System.out.println("\nTransformation complete!");
                System.out.println("Fixed " + transformationCount + " constructor calls.");
                System.out.println("Output written to: " + outputDir);
            } else {
                System.out.println("\nNo matching Constructor(Class) calls found.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}