package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.SpoonClassNotFoundException;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Find all method calls to parseEnchantment()
            List<CtInvocation<?>> methodCalls = model
                .getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation<?> invocation) {
                        CtExecutableReference<?> execRef = invocation.getExecutable();
                        if (execRef == null) {
                            return false;
                        }
                        
                        // Check if this is a call to parseEnchantment()
                        String methodName = execRef.getSimpleName();
                        if (!"parseEnchantment".equals(methodName)) {
                            return false;
                        }
                        
                        // In no-classpath mode, we can't reliably get the declaring type
                        // So we'll match based on method name and also check the target expression
                        // We'll look for patterns like XEnchantment.matchXEnchantment(...).get().parseEnchantment()
                        
                        // Get the target expression as string to check for XEnchantment references
                        String invocationStr = invocation.toString();
                        return invocationStr.contains("XEnchantment") && 
                               invocationStr.contains("parseEnchantment");
                    }
                });
            
            System.out.println("Found " + methodCalls.size() + " calls to parseEnchantment()");
            
            // Replace each parseEnchantment() call with getEnchant()
            for (CtInvocation<?> methodCall : methodCalls) {
                try {
                    // Get the executable reference and clone it
                    CtExecutableReference<?> oldExecRef = methodCall.getExecutable();
                    CtExecutableReference<?> newExecRef = methodCall.getFactory().Core().clone(oldExecRef);
                    
                    // Change the method name from parseEnchantment to getEnchant
                    newExecRef.setSimpleName("getEnchant");
                    
                    // Create a new method invocation with the updated executable reference
                    CtInvocation<?> newMethodCall = methodCall.getFactory().createInvocation(
                        methodCall.getTarget(),
                        newExecRef,
                        methodCall.getArguments()
                    );
                    
                    // Replace the old method call with the new one
                    methodCall.replace(newMethodCall);
                    System.out.println("Replaced parseEnchantment() with getEnchant() at: " + 
                                     methodCall.getPosition());
                } catch (Exception e) {
                    System.err.println("Error replacing method call at " + 
                                     methodCall.getPosition() + ": " + e.getMessage());
                }
            }
            
            // Don't use prettyprint to avoid corrupting files
            // Instead, we'll write the changes directly
            System.out.println("Transformation applied to " + methodCalls.size() + " method calls");
            
            // Save the changes
            for (spoon.reflect.cu.CompilationUnit cu : model.getCompilationUnits()) {
                try {
                    cu.save();
                } catch (Exception e) {
                    System.err.println("Error saving file: " + e.getMessage());
                }
            }
            
            System.out.println("Transformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}