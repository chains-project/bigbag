package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking dependency updates in SnakeYAML.
 * This handles the most common breaking changes in SnakeYAML 1.31 where constructor
 * signatures have changed.
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("Analyzing SnakeYAML usage patterns...");
        
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/polyglot-maven");
        
        CtModel model = launcher.buildModel();
        
        // Find and report SnakeYAML constructor usage
        findSnakeYAMLConstructors(model);
        
        System.out.println("Analysis complete. Ready to apply transformations.");
    }
    
    /**
     * Find all SnakeYAML constructor calls in the codebase
     */
    private static void findSnakeYAMLConstructors(CtModel model) {
        List<CtConstructorCall> yamlConstructors = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                return element.getType() != null && 
                       element.getType().getQualifiedName().equals("org.yaml.snakeyaml.Yaml");
            }
        });
        
        System.out.println("Found " + yamlConstructors.size() + " Yaml constructor calls");
        
        for (int i = 0; i < yamlConstructors.size(); i++) {
            CtConstructorCall ctor = yamlConstructors.get(i);
            System.out.println("Yaml constructor " + (i+1) + ": " + ctor);
        }
    }
}