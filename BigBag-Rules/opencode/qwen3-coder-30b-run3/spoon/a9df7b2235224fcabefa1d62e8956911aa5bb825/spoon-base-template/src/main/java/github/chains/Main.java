package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix SnakeYAML breaking changes in Maven projects.
 * Specifically addresses Serializer constructor signature changes in SnakeYAML 1.32.
 */
public class Main {
    public static void main(String[] args) {
        // Parse the target project
        Launcher launcher = new Launcher();
        launcher.setArgs(new String[]{
            "--source-classpath", "/workspace/polyglot-maven",
            "--output-type", "nooutput"
        });
        
        // Add input directories for the project sources
        launcher.addInputResource("/workspace/polyglot-maven");
        
        CtModel model = launcher.buildModel();
        
        // Find all Serializer constructor calls that need to be fixed - 4 arguments
        List<CtConstructorCall<?>> serializerCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            public boolean matches(CtConstructorCall<?> element) {
                // Match Serializer constructor with exactly 4 arguments
                return element.getType().getSimpleName().equals("Serializer") &&
                       element.getArguments().size() == 4;
            }
        });
        
        // Apply the transformation to fix the constructor calls by removing the extra argument
        for (CtConstructorCall<?> call : serializerCalls) {
            // Remove the fourth argument (Tag.MAP) that was part of the old API
            if (call.getArguments().size() >= 4) {
                call.getArguments().remove(3);
            }
        }
        
        System.out.println("Fixed " + serializerCalls.size() + " Serializer constructor calls");
    }
}