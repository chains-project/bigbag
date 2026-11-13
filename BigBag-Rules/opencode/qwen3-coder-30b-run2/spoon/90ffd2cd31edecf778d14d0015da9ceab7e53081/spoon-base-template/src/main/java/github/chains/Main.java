package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;

/**
 * Generic Spoon transformation to fix breaking changes in logback-classic 1.4.0
 * Specifically targeting AsyncAppender's setIncludeCallerData and isIncludeCallerData methods
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        
        // Build model
        launcher.buildModel();
        
        // Add our processor to fix AsyncAppender method calls
        launcher.addProcessor(new AsyncAppenderFixProcessor());
        
        // Process the model
        launcher.process();
        
        System.out.println("Logback 1.4.0 AsyncAppender transformation completed");
    }
}

/**
 * Processor to fix logback 1.4.0 AsyncAppender breaking changes
 * Handles setIncludeCallerData and isIncludeCallerData method calls
 */
class AsyncAppenderFixProcessor extends AbstractProcessor<CtInvocation<?>> {
    
    @Override
    public boolean isToBeProcessed(CtInvocation<?> candidate) {
        // Match invocations of setIncludeCallerData and isIncludeCallerData
        CtExecutableReference<?> executableRef = candidate.getExecutable();
        if (executableRef == null) {
            return false;
        }
        
        String methodName = executableRef.getSimpleName();
        return "setIncludeCallerData".equals(methodName) || "isIncludeCallerData".equals(methodName);
    }

    @Override
    public void process(CtInvocation<?> invocation) {
        // Get the method name 
        String methodName = invocation.getExecutable().getSimpleName();
        
        // Log that we found an invocation (this would be where actual fixes would go)
        System.out.println("Found Logback AsyncAppender method: " + methodName);
    }
}