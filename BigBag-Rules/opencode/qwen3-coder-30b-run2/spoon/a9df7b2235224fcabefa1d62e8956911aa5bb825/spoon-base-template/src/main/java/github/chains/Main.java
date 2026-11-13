package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.InvocationFilter;

import java.io.File;
import java.util.List;

/**
 * Simple Spoon transformation to fix SnakeYAML 1.32 breaking change
 * Replaces addTypeDescription() calls with direct typeTags manipulation
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory("./transformed-src");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        launcher.buildModel();
        Factory factory = launcher.getFactory();

        // Find all addTypeDescription method invocations
        List<CtInvocation> invocations = factory.getModel().filterChildren(
                new InvocationFilter("addTypeDescription"))
                .list();

        System.out.println("Found " + invocations.size() + " addTypeDescription calls");

        // Process each invocation
        for (CtInvocation invocation : invocations) {
            // Get the method that contains this invocation
            CtMethod method = invocation.getParent(CtMethod.class);
            if (method != null) {
                // Get the TypeDescription argument 
                List<CtStatement> arguments = invocation.getArguments();
                if (arguments.size() == 1) {
                    // Create replacement: this.typeTags.put(arg.getType(), arg)
                    String replacement = "this.typeTags.put(" + 
                                       arguments.get(0).toString() + ".getType(), " + 
                                       arguments.get(0).toString() + ")";
                    
                    invocation.replace(factory.createCodeSnippetStatement(replacement));
                }
            }
        }

        System.out.println("Transformations completed.");
        
        // Write the transformed code
        launcher.setSourceOutputDirectory("./transformed-src");
        launcher.process();
    }
}