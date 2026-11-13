package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in addComponent method signatures.
 * 
 * The breaking change is that addComponent methods changed from:
 *   addComponent(T component, Class role, String hint) 
 * to:
 *   addComponent(T component, String role, String hint)
 * 
 * This transformation finds calls to the old signature and updates them to use the new one.
 */
public class Main {
    public static void main(String[] args) {
        // Input directory - can be parameterized
        String inputDir = "/workspace/plexus-archiver/src/main/java";
        
        // Output directory
        String outputDir = "/workspace/plexus-archiver/src/main/java-fixed";
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all invocation expressions with addComponent method calls
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        int fixedCount = 0;
        
        // Process each invocation
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executableRef = invocation.getExecutable();
            
            // Check if it's an addComponent method call
            if ("addComponent".equals(executableRef.getSimpleName())) {
                // Check if it's the old signature (with Class parameter)
                List<CtTypeReference<?>> paramTypes = executableRef.getParameters();
                if (paramTypes.size() == 3 && 
                    "java.lang.Class".equals(paramTypes.get(1).getQualifiedName())) {
                    
                    // Get the arguments
                    List<CtExpression<?>> arguments = invocation.getArguments();
                    
                    // The transformation needs to convert:
                    // addComponent(component, MyRole.class, hint) 
                    // to:
                    // addComponent(component, "MyRole", hint)
                    
                    if (arguments.size() == 3) {
                        // Get the Class argument (second argument)
                        CtExpression<?> classArg = arguments.get(1);
                        
                        // Extract the class name from the class literal and replace with string literal
                        if (classArg instanceof CtTypeAccess) {
                            CtTypeAccess typeAccess = (CtTypeAccess) classArg;
                            String className = typeAccess.getAccessedType().getSimpleName();
                            
                            // Replace with string literal
                            CtLiteral<String> newLiteral = invocation.getFactory().createLiteral(className);
                            arguments.set(1, newLiteral);
                            
                            fixedCount++;
                        } else if (classArg instanceof CtFieldRead) {
                            // Handle cases where it's a field read like MyInterface.class
                            CtFieldRead fieldRead = (CtFieldRead) classArg;
                            String fieldName = fieldRead.getVariable().getSimpleName();
                            
                            // Try to extract class name from field name
                            if (fieldName.endsWith(".class")) {
                                String className = fieldName.substring(0, fieldName.length() - 6);
                                CtLiteral<String> newLiteral = invocation.getFactory().createLiteral(className);
                                arguments.set(1, newLiteral);
                                
                                fixedCount++;
                            }
                        }
                    }
                }
            }
        }
        
        // Process the model to apply changes
        launcher.process();
        
        System.out.println("Transformation completed successfully. Fixed " + fixedCount + " addComponent calls.");
    }
}