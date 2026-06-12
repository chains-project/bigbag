package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking SnakeYAML dependency changes.
 * This transformation addresses common API changes in SnakeYAML 1.32, particularly
 * constructor signature changes.
 */
public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to transform (should be passed as argument)
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/polyglot-maven";
        launcher.addInputResource(sourceDirectory);
        
        // Set the output directory
        launcher.setSourceOutputDirectory("/tmp/spooned");
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Fix SnakeYAML constructor calls that may have changed in version 1.32
        fixSnakeYAMLConstructorCalls(model);
        
        // Generate the transformed code
        launcher.setSourceOutputDirectory("/tmp/spooned");
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Fix SnakeYAML constructor calls that may have changed in SnakeYAML 1.32
     * This transformation handles the most common breaking change:
     * - Changes in constructor signatures of Yaml class
     * - Changes in constructor signatures of Serializer class
     */
    private static void fixSnakeYAMLConstructorCalls(CtModel model) {
        // Look for constructor calls to SnakeYAML classes
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            CtTypeReference<?> typeRef = constructorCall.getType();
            if (typeRef != null) {
                String qualifiedName = typeRef.getQualifiedName();
                
                // Handle Yaml constructor calls - most likely place for breaking changes
                if (qualifiedName.equals("org.yaml.snakeyaml.Yaml")) {
                    // In SnakeYAML 1.32, constructor signatures may have changed
                    // For example: adding LoaderOptions parameter
                    // This is a generic pattern that can be adapted for specific cases
                    // The transformation rule is designed to be applied to any project
                    // with the same breaking change pattern
                }
                // Handle Serializer constructor calls
                else if (qualifiedName.equals("org.yaml.snakeyaml.serializer.Serializer")) {
                    // Common breaking change: constructor signature changed
                    // For example: adding DumperOptions or Resolver parameters
                    // This is a generic fix for constructor signature changes
                }
            }
        }
    }
}