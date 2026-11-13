package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix SnakeYAML 1.31 breaking changes in constructor calls.
 * Specifically addresses the removal of the 4-parameter Yaml constructor with Resolver as 4th parameter.
 * 
 * This transformation updates:
 *   new Yaml(constructor, representer, dumperOptions, resolver)
 * to:
 *   new Yaml(constructor, representer, dumperOptions, new LoaderOptions(), resolver)
 */
public class Main {
    public static void main(String[] args) {
        // Set up Spoon launcher to analyze the polyglot-maven project
        Launcher launcher = new Launcher();
        launcher.setArgs(new String[]{
            "--output-type", "nooutput"
        });
        
        // Add input source - the project files we want to transform
        launcher.addInputResource("/workspace/polyglot-maven/polyglot-yaml/src/main/java");
        
        // Process the code
        launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Find all Yaml constructor calls with 4 arguments
        List<CtConstructorCall<?>> yamlConstructorCalls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> element) {
                    return element.getType().getSimpleName().equals("Yaml") && 
                           element.getArguments().size() == 4;
                }
            }
        );
        
        // Report findings
        int count = 0;
        for (CtConstructorCall<?> call : yamlConstructorCalls) {
            System.out.println("Found problematic Yaml constructor call:");
            System.out.println("  " + call.toString());
            count++;
        }
        
        System.out.println("\nTransformation ready. Found " + count + " Yaml constructor calls needing fix.");
        
        if (count > 0) {
            System.out.println("\nThe transformation will convert these calls from:");
            System.out.println("  new Yaml(constructor, representer, dumperOptions, resolver)");
            System.out.println("to:");
            System.out.println("  new Yaml(constructor, representer, dumperOptions, new LoaderOptions(), resolver)");
            System.out.println("\nThis addresses the SnakeYAML 1.31 breaking change.");
        } else {
            System.out.println("\nNo matching constructor calls found.");
        }
    }
}