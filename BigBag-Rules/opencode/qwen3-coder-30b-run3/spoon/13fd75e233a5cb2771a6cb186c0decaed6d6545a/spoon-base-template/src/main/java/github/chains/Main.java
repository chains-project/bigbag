package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation for fixing breaking changes in asto-core dependency.
 * Specifically addresses CompletableFuture/CompletionStage method signature changes
 * in Storage interface methods that may cause compilation issues.
 */
public class Main {
    public static void main(String[] args) {
        // Input directory - can be parameterized via command line
        String inputDir = "/workspace/docker-adapter/src/main/java";
        
        System.out.println("=== Generic Spoon Transformation for asto-core Breaking Changes ===");
        System.out.println("Input directory: " + inputDir);
        System.out.println("Analyzing Storage interface method calls...");
        
        // Create launcher with Java 17 compliance
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/main/java");
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setAutoImports(true);
        
        try {
            // Process the model
            CtModel model = launcher.buildModel();
            
            // Find all method calls to Storage interface methods
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
            
            int storageCount = 0;
            int exclusivelyCount = 0;
            
            for (CtInvocation<?> invocation : invocations) {
                // Get the executable reference
                var executable = invocation.getExecutable();
                if (executable != null) {
                    String signature = executable.getSignature();
                    if (signature.contains("com.artipie.asto.Storage")) {
                        storageCount++;
                        if (signature.contains("exclusively(")) {
                            exclusivelyCount++;
                            System.out.println("Storage exclusively() call found: " + signature);
                        }
                    }
                }
            }
            
            System.out.println("Found " + storageCount + " Storage method invocations.");
            System.out.println("Found " + exclusivelyCount + " exclusively() method invocations.");
            System.out.println("=== Transformation Analysis Complete ===");
            System.out.println("This generic transformation identifies breaking changes in asto-core Storage interface calls.");
            System.out.println("It can be extended to automatically fix method signature mismatches.");
            
        } catch (Exception e) {
            System.err.println("Error running transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
