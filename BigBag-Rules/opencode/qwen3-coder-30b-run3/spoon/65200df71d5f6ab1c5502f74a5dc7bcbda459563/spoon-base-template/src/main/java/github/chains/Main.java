package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.processing.AbstractProcessor;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        
        // Create Spoon launcher to process the code
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.buildModel();
        
        // Process the model to fix the sonarlint-core API issue
        launcher.addProcessor(new SonarLintApiMigrationRule());
        launcher.run();
        
        // Print a success message
        System.out.println("AnalysisEngineConfiguration API migration completed.");
    }
    
    public static class SonarLintApiMigrationRule extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // Match the specific broken API call pattern
            if (candidate.getExecutable() == null) {
                return false;
            }
            
            // Check if this is the problematic method call
            return "addEnabledLanguages".equals(candidate.getExecutable().getSimpleName());
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            // Remove the call to addEnabledLanguages since it was removed in sonarlint-core 8.19.0.72745
            invocation.delete();
        }
    }
}