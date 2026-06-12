package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Set inputs
        launcher.addInputResource("/workspace/docker-adapter/src/test/java");
        launcher.addInputResource("/workspace/docker-adapter/src/main/java");
        
        // Output directory for the transformed files
        launcher.setSourceOutputDirectory("/workspace/spooned_output");
        
        // Configure environment
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);

        // Add the processor to fix Hamcrest constructors
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> element) {
                String typeName = element.getType().getQualifiedName();
                if ("org.hamcrest.core.StringContains".equals(typeName) || 
                    "org.hamcrest.core.StringStartsWith".equals(typeName)) {
                    
                    List<CtExpression<?>> arguments = element.getArguments();
                    // Check if it has 2 arguments (boolean, String)
                    if (arguments.size() == 2) {
                        // Remove the first argument (the boolean)
                        element.removeArgument(arguments.get(0));
                        System.out.println("Fixed " + typeName + " at " + element.getPosition());
                    }
                }
            }
        });

        launcher.run();
    }
}