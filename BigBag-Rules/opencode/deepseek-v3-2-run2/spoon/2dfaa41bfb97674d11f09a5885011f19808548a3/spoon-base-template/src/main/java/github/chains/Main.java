package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir>");
            System.err.println("Example: java Main /path/to/project/src /path/to/output");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Creating generic transformation for Mockito 5.x breaking change...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            // Create launcher with the source directory
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(false);  // Don't mess with imports
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setPreserveLineNumbers(true);
            launcher.addInputResource(sourceDir);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Apply transformation
            int transformedCount = transformGetArgumentAtToGetArgument(model);
            
            if (transformedCount > 0) {
                // Output transformed code
                launcher.setSourceOutputDirectory(new File(outputDir));
                launcher.prettyprint();
                
                System.out.println("Transformation completed successfully!");
                System.out.println("Transformed " + transformedCount + " occurrences of getArgumentAt");
                System.out.println("Transformed code written to: " + outputDir);
            } else {
                System.out.println("No occurrences of getArgumentAt found to transform.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformGetArgumentAtToGetArgument(CtModel model) {
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                // Check if this is a getArgumentAt method call
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null) {
                    return false;
                }
                
                String methodName = execRef.getSimpleName();
                return "getArgumentAt".equals(methodName);
            }
        });
        
        System.out.println("Found " + invocations.size() + " occurrences of getArgumentAt to transform");
        
        int transformed = 0;
        
        // Transform each occurrence
        for (CtInvocation<?> invocation : invocations) {
            System.out.println("Transforming: " + invocation.getPosition());
            
            // Create new method reference for getArgument
            CtExecutableReference<?> oldExecRef = invocation.getExecutable();
            Factory factory = invocation.getFactory();
            
            // Create new executable reference for getArgument
            CtExecutableReference<?> newExecRef = factory.createExecutableReference();
            newExecRef.setDeclaringType(oldExecRef.getDeclaringType());
            newExecRef.setSimpleName("getArgument");
            
            // Create new invocation
            CtInvocation<?> newInvocation = factory.createInvocation(
                invocation.getTarget(),
                newExecRef,
                invocation.getArguments()
            );
            
            // Replace the old invocation with the new one
            invocation.replace(newInvocation);
            transformed++;
            
            System.out.println("  -> Replaced with getArgument");
        }
        
        return transformed;
    }
}