package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.StandardEnvironment;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src");
            System.exit(1);
        }

        String sourcePath = args[0];
        System.out.println("Applying transformation to: " + sourcePath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        
        // Configure launcher
        launcher.addInputResource(sourcePath);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setShouldCompile(false);
        
        // Build model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations that match the pattern:
        // FilterBuilder.apply(String)
        // Since we're using NoClasspath mode, we need to be more lenient in matching
        var invocations = Query.getElements(model.getRootPackage(), new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                // Check if this is a method call to 'apply' with one String parameter
                if (!"apply".equals(invocation.getExecutable().getSimpleName())) {
                    return false;
                }
                
                // Check if it has exactly one parameter
                if (invocation.getArguments().size() != 1) {
                    return false;
                }
                
                // Try to get the target expression
                var target = invocation.getTarget();
                if (target == null) {
                    return false;
                }
                
                // In NoClasspath mode, we can't reliably check types
                // So we'll use a more permissive check based on common patterns
                try {
                    // Check if the target looks like "new FilterBuilder().include(...)"
                    // or any expression ending with FilterBuilder
                    String targetCode = target.toString();
                    
                    // Pattern 1: new FilterBuilder().include(...).apply(...)
                    if (targetCode.contains("new FilterBuilder()") || 
                        targetCode.contains("FilterBuilder")) {
                        return true;
                    }
                    
                    // Pattern 2: Check if parent method/class imports FilterBuilder
                    // or uses it in variable declarations
                    var parentClass = invocation.getParent(CtClass.class);
                    if (parentClass != null) {
                        String classCode = parentClass.toString();
                        if (classCode.contains("FilterBuilder") || 
                            classCode.contains("org.reflections.util.FilterBuilder")) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    // If we can't analyze, be conservative
                    System.err.println("Warning: Could not analyze invocation: " + invocation);
                }
                
                return false;
            }
        });
        
        System.out.println("Found " + invocations.size() + " invocations of FilterBuilder.apply(String)");
        
        // Apply transformation: replace 'apply' with 'test'
        int transformed = 0;
        for (CtInvocation<?> invocation : invocations) {
            try {
                // Simple approach: just change the method name
                invocation.getExecutable().setSimpleName("test");
                transformed++;
                
                System.out.println("Transformed: " + invocation.getPosition());
            } catch (Exception e) {
                System.err.println("Error transforming invocation at " + invocation.getPosition() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Successfully transformed " + transformed + " invocations");
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(new File(sourcePath).getParentFile());
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Code written to: " + new File(sourcePath).getParent());
    }
}