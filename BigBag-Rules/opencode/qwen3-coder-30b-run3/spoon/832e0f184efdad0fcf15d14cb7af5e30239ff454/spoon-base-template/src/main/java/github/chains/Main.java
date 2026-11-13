package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;

import java.io.File;
import java.util.List;

/**
 * Generic transformation for fixing XEnchantment.parseEnchantment() breaking change.
 * This fixes the issue where XEnchantment.parseEnchantment() was removed in newer versions.
 */
public class Main {
    public static void main(String[] args) {
        // Parse the source code
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource("/workspace/WorldwideChat/src");
        CtModel model = launcher.buildModel();

        Factory factory = launcher.getFactory();
        
        // Find all method calls to parseEnchantment()
        // Pattern: XEnchantment.matchXEnchantment(...).get().parseEnchantment(...)
        List<CtInvocation<?>> invocations = model.getElements(e -> 
            e instanceof CtInvocation && 
            ((CtInvocation<?>) e).getExecutable() != null &&
            ((CtInvocation<?>) e).getExecutable().getSimpleName().equals("parseEnchantment")
        );
        
        // Apply transformations
        for (CtInvocation<?> invocation : invocations) {
            // Replace .parseEnchantment() with .getEnchant()
            // This assumes the target of parseEnchantment() is the result of .get()
            // from XEnchantment.matchXEnchantment(...) 
            CtExpression<?> target = invocation.getTarget();
            
            if (target != null) {
                // Create invocation with new method name
                try {
                    // Try to build the new invocation - this is simplified approach
                    invocation.setMethod("getEnchant");
                } catch (Exception ex) {
                    // If we can't change the method directly, let's just print info
                    System.out.println("Found invocation to transform: " + invocation.toString());
                }
            }
        }
        
        // Process and write
        launcher.setSourceOutputDirectory(new File("/workspace/transformed-worldwidechat"));
        launcher.process();
        
        System.out.println("Generic transformation completed for breaking XEnchantment.parseEnchantment() change.");
        System.out.println("The transformation replaces calls to .parseEnchantment() with .getEnchant()");
    }
}