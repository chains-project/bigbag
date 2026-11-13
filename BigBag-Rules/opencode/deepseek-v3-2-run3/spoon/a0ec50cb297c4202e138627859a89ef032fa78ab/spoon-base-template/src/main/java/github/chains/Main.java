package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        
        // Create transformation for Connection.accept() calls
        launcher.addProcessor(new spoon.processing.AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                try {
                    // Check if this is a Connection.accept() call
                    if (isConnectionAcceptCall(invocation)) {
                        // Check if we have at least 3 arguments (rsstatus, headers, body)
                        if (invocation.getArguments().size() >= 3) {
                            // Get the headers argument (second argument, index 1)
                            var headersArg = invocation.getArguments().get(1);
                            
                            // Get the type of the headers argument
                            CtTypeReference<?> argType = headersArg.getType();
                            if (argType == null) {
                                return;
                            }
                            
                            // Check if the argument type is Headers already
                            String typeName = argType.getQualifiedName();
                            if (typeName != null && typeName.equals("com.artipie.http.Headers")) {
                                // Already Headers type, no need to transform
                                return;
                            }
                            
                            // Check if it's Iterable<Map.Entry<String, String>> or similar
                            // We'll transform any non-Headers argument to be safe
                            Factory factory = invocation.getFactory();
                            
                            // Create type reference for Headers.From
                            var headersFromType = factory.createCtTypeReference(
                                com.artipie.http.Headers.From.class
                            );
                            
                            // Create constructor call: new Headers.From(headersArg)
                            CtConstructorCall<?> newHeadersFrom = factory.createConstructorCall(
                                headersFromType,
                                headersArg
                            );
                            
                            // Replace the old headers argument with the new one
                            invocation.getArguments().set(1, newHeadersFrom);
                            
                            System.out.println("Transformed Connection.accept() call at: " + 
                                invocation.getPosition());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error transforming invocation at " + 
                        invocation.getPosition() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            private boolean isConnectionAcceptCall(CtInvocation<?> invocation) {
                try {
                    // Check if method is named "accept"
                    if (!"accept".equals(invocation.getExecutable().getSimpleName())) {
                        return false;
                    }
                    
                    // Try to get the target expression
                    var target = invocation.getTarget();
                    if (target == null) {
                        return false;
                    }
                    
                    // Get target type
                    var targetType = target.getType();
                    if (targetType == null) {
                        return false;
                    }
                    
                    // Check if target type is Connection
                    String typeName = targetType.getQualifiedName();
                    return typeName != null && 
                           typeName.equals("com.artipie.http.Connection");
                    
                } catch (Exception e) {
                    return false;
                }
            }
        });
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}