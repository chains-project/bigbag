package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Validate arguments
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory> [output-directory]");
            System.err.println("  source-directory: Root directory of Java source files to transform");
            System.err.println("  output-directory: Optional directory for transformed files (default: in-place)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir;
        
        System.out.println("Running SnakeYAML 2.0 migration transformation...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        System.out.println("Building AST model...");
        launcher.buildModel();
        
        System.out.println("Scanning for org.yaml.snakeyaml.constructor.Constructor instantiation...");
        
        // Find all constructor calls for org.yaml.snakeyaml.constructor.Constructor
        List<CtConstructorCall<?>> constructorCalls = launcher.getModel()
            .getRootPackage()
            .getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getExecutable() == null) {
                        return false;
                    }
                    
                    // Get the type being constructed
                    CtTypeReference<?> constructedType = constructorCall.getType();
                    if (constructedType == null) {
                        return false;
                    }
                    
                    // Check if it's org.yaml.snakeyaml.constructor.Constructor
                    String qualifiedName = constructedType.getQualifiedName();
                    boolean isSnakeYamlConstructor = "org.yaml.snakeyaml.constructor.Constructor".equals(qualifiedName);
                    
                    // Also check for simple name Constructor which might be imported
                    boolean isSimpleConstructor = "Constructor".equals(constructedType.getSimpleName());
                    
                    if (isSnakeYamlConstructor || isSimpleConstructor) {
                        // Check if it's a no-argument constructor
                        return constructorCall.getArguments().isEmpty();
                    }
                    
                    return false;
                }
            });
        
        System.out.println("Found " + constructorCalls.size() + " no-argument Constructor() calls to transform");
        
        // Transform each constructor call
        int transformed = 0;
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            try {
                System.out.println("Transforming Constructor() call at position: " + 
                                 constructorCall.getPosition().toString());
                
                // Create new LoaderOptions() argument
                CtTypeReference<?> loaderOptionsType = launcher.getFactory()
                    .createReference("org.yaml.snakeyaml.LoaderOptions");
                
                CtConstructorCall<?> loaderOptionsConstructor = launcher.getFactory()
                    .createConstructorCall(loaderOptionsType);
                
                // Replace the no-arg constructor with Constructor(new LoaderOptions())
                constructorCall.setArguments(List.of(loaderOptionsConstructor));
                
                // Note: Spoon's autoImports environment setting will handle imports automatically
                
                transformed++;
            } catch (Exception e) {
                System.err.println("Error transforming constructor call at " + 
                                 constructorCall.getPosition() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Successfully transformed " + transformed + " Constructor() calls");
        
        // Write transformed code
        System.out.println("Generating transformed source files...");
        launcher.prettyprint();
        
        System.out.println("Transformation complete!");
    }
}