package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Create and run the processor
            JAXBToStringStrategyFixProcessor processor = new JAXBToStringStrategyFixProcessor();
            launcher.addProcessor(processor);
            
            // Process the model
            launcher.process();
            
            // Apply transformations
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Modified " + processor.getModifiedCount() + " files");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static class JAXBToStringStrategyFixProcessor extends AbstractProcessor<CtClass<?>> {
        private int modifiedCount = 0;
        
        @Override
        public void process(CtClass<?> clazz) {
            // Find all method invocations in the class
            List<CtInvocation<?>> invocations = clazz.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    return isJAXBToStringStrategyGetInstanceCall(invocation);
                }
            });
            
            if (!invocations.isEmpty()) {
                for (CtInvocation<?> invocation : invocations) {
                    // Replace method call with field access using string manipulation
                    replaceGetInstanceWithFieldAccess(invocation);
                    modifiedCount++;
                }
                
                System.out.println("Modified class: " + clazz.getQualifiedName());
            }
        }
        
        private boolean isJAXBToStringStrategyGetInstanceCall(CtInvocation<?> invocation) {
            // Check if this is a call to getInstance() on JAXBToStringStrategy
            try {
                if (invocation.getExecutable() != null && 
                    invocation.getExecutable().getSimpleName().equals("getInstance")) {
                    
                    CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
                    if (declaringType != null && 
                        declaringType.getQualifiedName().equals("org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy")) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Skip if we can't analyze the invocation
            }
            return false;
        }
        
        private void replaceGetInstanceWithFieldAccess(CtInvocation<?> invocation) {
            Factory factory = invocation.getFactory();
            
            // Create field access: JAXBToStringStrategy.INSTANCE
            // Use CodeSnippetExpression to create the field access
            String fieldAccessCode = "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy.INSTANCE";
            
            // Create a code snippet expression for the field access
            // This is simpler than trying to build the AST manually
            invocation.replace(factory.createCodeSnippetExpression(fieldAccessCode));
        }
        
        public int getModifiedCount() {
            return modifiedCount;
        }
    }
}