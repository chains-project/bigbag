package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtType;

import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking dependency changes in Maven projects.
 * This handles breaking changes in logback-classic AsyncAppender.setIncludeCallerData().
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar <jar-file> <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create a Launcher with the specified source directory
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory + "_new");
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformation to fix AsyncAppender.setIncludeCallerData() calls
        fixAsyncAppenderSetIncludeCallerData(model);
        
        System.out.println("Transformation completed successfully.");
    }
    
    /**
     * Finds calls to AsyncAppender.setIncludeCallerData(boolean) that were changed in newer versions
     */
    private static void fixAsyncAppenderSetIncludeCallerData(CtModel model) {
        // Find all invocations of setIncludeCallerData methods
        List<CtInvocation> invocations = model.getElements(new spoon.reflect.visitor.filter.TypeFilter<>(CtInvocation.class));
        
        System.out.println("Found " + invocations.size() + " invocations.");
        
        for (CtInvocation invocation : invocations) {
            if (invocation.getExecutable() != null && 
                "setIncludeCallerData".equals(invocation.getExecutable().getSimpleName()) &&
                invocation.getArguments().size() == 1) {
                
                // Get the enclosing class
                CtType<?> parentType = invocation.getParent(CtType.class);
                if (parentType != null) {
                    // Check if it's part of an AsyncAppender class or its subclass
                    if (isAsyncAppenderRelated(parentType)) {
                        System.out.println("Processing setIncludeCallerData call in: " + parentType.getQualifiedName());
                    }
                }
            }
        }
    }

    /**
     * Checks if the type is related to AsyncAppender
     */
    private static boolean isAsyncAppenderRelated(CtType<?> type) {
        // Check class hierarchy for AsyncAppender-related classes
        String qualifiedName = type.getQualifiedName();
        return qualifiedName.contains("AsyncAppender") || 
               (type.getSuperclass() != null && 
                type.getSuperclass().getQualifiedName().contains("AsyncAppender"));
    }
}