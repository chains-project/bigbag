package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to analyze
        launcher.addInputResource("/workspace/WorldwideChat/src");
        
        // Set the output directory for the transformed code
        launcher.setSourceOutputDirectory("/workspace/WorldwideChat-transformed/src");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all invocations of the pattern: XEnchantment.matchXEnchantment(...).get().parseEnchantment()
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                // Check if this invocation is calling parseEnchantment on XEnchantment
                if (element.getExecutable() != null && 
                    element.getExecutable().getSimpleName().equals("parseEnchantment") &&
                    element.getTarget() != null) {
                    
                    // Check if the target is a get() call
                    if (element.getTarget() instanceof CtInvocation) {
                        CtInvocation<?> getInvocation = (CtInvocation<?>) element.getTarget();
                        
                        // Check if the get() call's target is a matchXEnchantment call
                        if (getInvocation.getExecutable() != null && 
                            getInvocation.getExecutable().getSimpleName().equals("get") &&
                            getInvocation.getTarget() instanceof CtInvocation) {
                            
                            CtInvocation<?> matchInvocation = (CtInvocation<?>) getInvocation.getTarget();
                            
                            // Check if the match invocation is matchXEnchantment
                            if (matchInvocation.getExecutable() != null && 
                                matchInvocation.getExecutable().getSimpleName().equals("matchXEnchantment")) {
                                
                                // This is the exact pattern we want to fix
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
        
        System.out.println("Found " + invocations.size() + " invocations to transform");
        
        // Transform the invocations
        for (CtInvocation<?> invocation : invocations) {
            // Get the target of parseEnchantment (which is the get() call)
            CtExpression<?> target = invocation.getTarget();
            if (target instanceof CtInvocation) {
                CtInvocation<?> getInvocation = (CtInvocation<?>) target;
                
                // Replace the entire chain: matchXEnchantment(...).get().parseEnchantment()
                // With just: matchXEnchantment(...).get()
                
                // Create a copy of the get() invocation (which is matchXEnchantment(...).get())
                // This is the expression we want to replace the entire parseEnchantment call with
                
                // Replace the old invocation with the get() invocation
                invocation.replace(getInvocation);
            }
        }
        
        // Write the transformed code
        launcher.process();
        
        System.out.println("Transformation completed");
    }
}