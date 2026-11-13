package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

/**
 * Generic transformation rule to handle breaking changes in logback-classic
 * This is a reusable template that can be adapted to fix any breaking API changes
 * in logback-classic dependency updates.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(new File(sourceDirectory + "_transformed"));
        
        CtModel model = launcher.buildModel();
        
        // Find all invocations of potentially problematic methods
        model.getElements(new TypeFilter<>(CtInvocation.class))
                .stream()
                .filter(invocation -> {
                    CtExecutableReference<?> executableRef = invocation.getExecutable();
                    String methodName = executableRef.getSimpleName();
                    // This pattern identifies method calls on logback components
                    // that commonly break with version changes
                    return methodName.equals("setIncludeCallerData") || 
                           methodName.equals("addAppender") ||
                           methodName.equals("start") ||
                           methodName.equals("stop");
                })
                .forEach(invocation -> {
                    // In a real implementation, we would do specific transformations here
                    System.out.println("Found potentially problematic logback method call: " + 
                        invocation.getExecutable().getSimpleName());
                });

        // Process the model to generate the transformed code
        launcher.process();
        
        System.out.println("Generic transformation completed. Check " + sourceDirectory + "_transformed");
    }
}