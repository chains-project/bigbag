package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.io.File;
import java.util.List;

/**
 * A generic Spoon transformation to fix the breaking API change in tinspin-indexes 1.8.0+
 * where KDTree.create(int, PointDistanceFunction) is deprecated/removed.
 * 
 * BREAKING CHANGE ANALYSIS:
 * - Old API pattern: KDTree.create(int dimensions, PointDistanceFunction distanceFunction)
 * - New API pattern: KDTree.create(int dimensions)  
 * - Structural transformation: Remove the second argument (PointDistanceFunction)
 * - Rationale: KDTree now always uses L2 (Euclidean) distance internally
 * 
 * This transformation:
 * 1. Finds all calls to KDTree.create() with 2 arguments
 * 2. Removes the second argument (distance function)
 * 3. Preserves the first argument (dimensions)
 * 
 * USAGE:
 *   java -jar spoon-transformation.jar <source-directory>
 *   
 * EXAMPLE:
 *   java -jar spoon-transformation.jar /path/to/project/src/main/java
 *   
 * OUTPUT:
 *   Transformed code written to <source-directory>-transformed
 *   
 * NOTE: This transformation assumes the removed distance function was L2 (Euclidean).
 * If a non-L2 distance function was used, manual adjustment may be needed.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        File sourceDir = new File(sourcePath);
        
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("Error: Source directory does not exist: " + sourcePath);
            System.exit(1);
        }
        
        System.out.println("Applying transformation to: " + sourcePath);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.addInputResource(sourcePath);
            
            // Build the model
            CtModel model = launcher.buildModel();
            
            // Find all method invocations
            List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
            
            int transformedCount = 0;
            
            // Apply transformation to each invocation
            for (CtInvocation<?> invocation : invocations) {
                try {
                    // Check if this is a call to KDTree.create(int, PointDistanceFunction)
                    CtExecutableReference<?> execRef = invocation.getExecutable();
                    String methodName = execRef.getSimpleName();
                    
                    if ("create".equals(methodName)) {
                        // Check if it's KDTree.create
                        CtTypeReference<?> declaringType = execRef.getDeclaringType();
                        if (declaringType != null) {
                            String typeName = declaringType.getQualifiedName();
                            if (typeName != null && typeName.equals("org.tinspin.index.kdtree.KDTree")) {
                                // Check if it has 2 arguments (int, PointDistanceFunction)
                                if (invocation.getArguments().size() == 2) {
                                    System.out.println("Found KDTree.create() call at: " + 
                                        invocation.getPosition().getFile().getName() + ":" + 
                                        invocation.getPosition().getLine());
                                    
                                    // Keep only the first argument (dimensions)
                                    var firstArg = invocation.getArguments().get(0);
                                    invocation.getArguments().clear();
                                    invocation.getArguments().add(firstArg);
                                    
                                    // Note: We could add a comment here, but skipping for simplicity
                                    // The transformation removes the distance function parameter since
                                    // KDTree now always uses L2 distance internally
                                    
                                    transformedCount++;
                                    System.out.println("  Transformed: " + invocation);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore and continue with next invocation
                    System.err.println("Warning: Could not process invocation at " + 
                        invocation.getPosition() + ": " + e.getMessage());
                }
            }
            
            System.out.println("Transformed " + transformedCount + " KDTree.create() calls");
            
            // Output transformed code
            String outputPath = sourcePath + "-transformed";
            launcher.setSourceOutputDirectory(outputPath);
            launcher.prettyprint();
            
            System.out.println("\nTransformation complete. Output written to: " + outputPath);
            System.out.println("\nNote: This transformation:");
            System.out.println("1. Removes the PointDistanceFunction argument from KDTree.create() calls");
            System.out.println("2. Assumes the removed distance function was L2 (Euclidean)");
            System.out.println("3. KDTree now always uses L2 distance internally");
            System.out.println("\nIf you were using a non-L2 distance function, you may need to:");
            System.out.println("- Use a different data structure that supports custom distance functions");
            System.out.println("- Or adjust your algorithm to work with L2 distance");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}