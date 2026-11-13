package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Applying XSeries XEnchantment API transformation to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all method calls to parseEnchantment()
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                return element.getExecutable() != null && 
                       "parseEnchantment".equals(element.getExecutable().getSimpleName());
            }
        });
        
        System.out.println("Found " + invocations.size() + " occurrences of parseEnchantment()");
        
        // Apply transformation: parseEnchantment() -> getEnchant()
        int transformationCount = 0;
        for (CtInvocation<?> invocation : invocations) {
            try {
                System.out.println("Transforming invocation at: " + invocation.getPosition());
                
                // Use the factory to create a new executable reference with the same signature
                // but different method name
                var factory = launcher.getFactory();
                var oldExecutable = invocation.getExecutable();
                
                // Create new executable reference
                var newExecutable = factory.createExecutableReference();
                newExecutable.setSimpleName("getEnchant");
                
                // Copy important properties from old executable
                newExecutable.setDeclaringType(oldExecutable.getDeclaringType());
                
                if (oldExecutable.getActualTypeArguments() != null) {
                    newExecutable.setActualTypeArguments(oldExecutable.getActualTypeArguments());
                }
                
                if (oldExecutable.getParameters() != null) {
                    newExecutable.setParameters(oldExecutable.getParameters());
                }
                
                // This is a bit of a hack - we need to cast due to type erasure
                // but it should work since we're just changing the method name
                invocation.setExecutable((spoon.reflect.reference.CtExecutableReference) newExecutable);
                transformationCount++;
                
            } catch (Exception e) {
                System.err.println("Error transforming invocation: " + e.getMessage());
            }
        }
        
        // Output transformed code
        if (transformationCount > 0) {
            System.out.println("\nApplied " + transformationCount + " transformations");
            
            // Set output directory (same as input to overwrite)
            File outputDir = new File(sourceDir).getParentFile();
            if (outputDir != null && outputDir.exists()) {
                launcher.getEnvironment().setSourceOutputDirectory(outputDir);
            }
            
            // Pretty print all transformed types
            launcher.prettyprint();
            System.out.println("Transformed code written to: " + outputDir);
        } else {
            System.out.println("No transformations applied - no parseEnchantment() calls found");
        }
        
        System.out.println("\nTransformation complete!");
    }
}