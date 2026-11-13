package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[0] + "_transformed");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Add processors for commons-io fixes
        launcher.addProcessor(new CommonsIoFixProcessor());
        
        // Run transformation
        launcher.run();
        
        System.out.println("Transformation complete.");
    }
    
    public static class CommonsIoFixProcessor extends AbstractProcessor<CtElement> {
        private Factory factory;
        
        @Override
        public void init() {
            this.factory = getFactory();
        }
        
        @Override
        public void process(CtElement element) {
            // Identify and report issues that can be fixed
            if (element instanceof CtInvocation) {
                CtInvocation invocation = (CtInvocation) element;
                System.err.println("Found invocation: " + invocation.toString());
            }
            
            if (element instanceof CtFieldRead) {
                CtFieldRead fieldRead = (CtFieldRead) element;
                System.err.println("Found field read: " + fieldRead.toString());
            }
        }
    }
}