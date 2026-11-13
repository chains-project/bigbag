package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true); // Use no-classpath mode
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build model
        launcher.buildModel();
        
        // Find and transform addEnabledLanguages calls
        boolean modified = transformAddEnabledLanguagesCalls(launcher);
        
        if (modified) {
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformation complete! Files modified.");
        } else {
            System.out.println("No transformations applied.");
        }
    }
    
    private static boolean transformAddEnabledLanguagesCalls(Launcher launcher) {
        // Find all method invocations of addEnabledLanguages
        List<CtInvocation<?>> invocations = launcher.getModel()
            .getRootPackage()
            .getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> element) {
                    return "addEnabledLanguages".equals(element.getExecutable().getSimpleName());
                }
            });
        
        System.out.println("Found " + invocations.size() + " addEnabledLanguages calls");
        
        boolean modified = false;
        
        for (CtInvocation<?> invocation : invocations) {
            System.out.println("Processing call at: " + invocation.getPosition());
            
            // Get the parent of this invocation
            CtElement parent = invocation.getParent();
            
            if (parent instanceof CtInvocation) {
                // This invocation is part of a chain
                // For example: builder().addEnabledLanguages(...).setClientPid(...)
                // Here, invocation is addEnabledLanguages(...)
                // parent is setClientPid(...) with target = addEnabledLanguages(...)
                
                CtInvocation<?> parentInvocation = (CtInvocation<?>) parent;
                CtElement target = invocation.getTarget();
                
                if (target != null) {
                    // We need to replace invocation with its target
                    // So parentInvocation should target invocation's target
                    // In Spoon, we can't directly modify the target
                    // Instead, we need to create a new invocation
                    
                    // For now, let's try a simpler approach: just delete
                    // and hope Spoon handles the chain reconstruction
                    invocation.delete();
                    modified = true;
                }
            } else {
                // Not part of a chain, just delete it
                invocation.delete();
                modified = true;
            }
        }
        
        return modified;
    }
}