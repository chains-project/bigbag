package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix jaxb2-basics-runtime breaking changes.
 * Fixes missing getInstance() method calls in strategy classes.
 */
public class Main {
    public static void main(String[] args) {
        // Process the Billy project
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Set the source directory to process
        if (args.length > 0) {
            launcher.addInputResource(args[0]);
        } else {
            launcher.addInputResource("/workspace/billy");
        }
        
        launcher.addProcessor(new FixStrategyGetInstanceProcessor());
        launcher.run();
        
        System.out.println("Transformation completed.");
    }
}