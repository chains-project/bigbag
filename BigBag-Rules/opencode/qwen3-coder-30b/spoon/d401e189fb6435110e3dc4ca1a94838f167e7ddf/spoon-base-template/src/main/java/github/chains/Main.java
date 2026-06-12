package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.processing.AbstractProcessor;
import spoon.reflect.factory.Factory;

/**
 * Generic Spoon transformation to fix breaking dependency updates in logback-classic.
 * This transformation addresses breaking changes in logback API where interfaces like
 * org.slf4j.spi.LoggingEventAware have been removed.
 */
public class Main {
    public static void main(String[] args) {
        // Parse the source code
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pdb/src");
        launcher.setSourceOutputDirectory("/workspace/pdb/src");
        
        // Process the model
        CtModel model = launcher.buildModel();
        
        // Apply the transformation
        model.processWith(new LogbackBreakingChangeFixer());
        
        // Write the modified code back
        launcher.writeProcessedSourceFiles();
        
        System.out.println("Logback breaking change transformation applied successfully");
    }
    
    /**
     * Processor that fixes breaking changes in logback API
     */
    public static class LogbackBreakingChangeFixer extends AbstractProcessor<CtClass<?>> {
        
        @Override
        public void process(CtClass<?> ctClass) {
            Factory factory = ctClass.getFactory();
            
            // Remove any references to LoggingEventAware interface
            // This is a common breaking change in logback 1.4.4+
            // Remove imports of LoggingEventAware
            factory.CompilationUnit().getImports().removeIf(imp -> 
                imp.getReference() != null && 
                imp.getReference().toString().contains("org.slf4j.spi.LoggingEventAware")
            );
            
            // Remove interface implementations of LoggingEventAware
            ctClass.getSuperInterfaces().removeIf(iface -> 
                iface.toString().contains("org.slf4j.spi.LoggingEventAware")
            );
        }
    }
}