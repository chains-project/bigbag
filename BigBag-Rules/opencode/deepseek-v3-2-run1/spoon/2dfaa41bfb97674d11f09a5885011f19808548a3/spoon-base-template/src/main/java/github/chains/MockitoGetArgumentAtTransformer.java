package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.util.List;

/**
 * Generic transformation rule for Mockito 5.1.1 breaking API change:
 * Transforms InvocationOnMock.getArgumentAt(int, Class<T>) to getArgument(int, Class<T>)
 * 
 * This transformation is applicable to ANY Java project affected by this
 * Mockito API change. Simply run it on the source code directory.
 */
public class MockitoGetArgumentAtTransformer {
    
    /**
     * Main entry point for the transformation.
     * 
     * @param args Command line arguments: <sourceDir> <outputDir>
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar mockito-transformer.jar <sourceDir> <outputDir>");
            System.err.println("  sourceDir: Path to the source code directory to transform");
            System.err.println("  outputDir: Path where transformed code will be written");
            System.err.println("\nExample: java -jar mockito-transformer.jar /path/to/project/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("=== Mockito 5.1.1 API Migration Transformer ===");
        System.out.println("Transforming: InvocationOnMock.getArgumentAt() -> getArgument()");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println();
        
        try {
            int transformations = transformProject(sourceDir, outputDir);
            
            System.out.println("\n=== Transformation Summary ===");
            System.out.println("Total transformations applied: " + transformations);
            if (transformations > 0) {
                System.out.println("Transformed code written to: " + outputDir);
                System.out.println("\nNote: This transformation fixes the Mockito 5.1.1 breaking change:");
                System.out.println("  - Old API: InvocationOnMock.getArgumentAt(int index, Class<T> clazz)");
                System.out.println("  - New API: InvocationOnMock.getArgument(int index, Class<T> clazz)");
            } else {
                System.out.println("No getArgumentAt() calls found. Project is already compatible with Mockito 5.1.1.");
            }
        } catch (Exception e) {
            System.err.println("Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transforms a project by finding and replacing getArgumentAt() calls.
     * 
     * @param sourceDir Source code directory
     * @param outputDir Output directory for transformed code
     * @return Number of transformations applied
     */
    private static int transformProject(String sourceDir, String outputDir) {
        Launcher launcher = new Launcher();
        
        // Configure Spoon for maximum compatibility
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true); // Don't require dependencies
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        
        // Use Sniper printer to preserve formatting as much as possible
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));
        
        // Add input source directory
        launcher.addInputResource(sourceDir);
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        
        int transformationCount = 0;
        
        System.out.println("Scanning for getArgumentAt() calls...");
        
        // Transform getArgumentAt to getArgument
        for (CtInvocation<?> invocation : invocations) {
            if (invocation.getExecutable() != null && 
                "getArgumentAt".equals(invocation.getExecutable().getSimpleName())) {
                
                // Apply the transformation
                invocation.getExecutable().setSimpleName("getArgument");
                transformationCount++;
                
                // Log the transformation
                CtClass<?> parentClass = invocation.getParent(CtClass.class);
                if (parentClass != null) {
                    String className = parentClass.getQualifiedName();
                    int lineNumber = invocation.getPosition().getLine();
                    System.out.println("  ✓ " + className + ":" + lineNumber);
                }
            }
        }
        
        if (transformationCount > 0) {
            System.out.println("\nWriting transformed code...");
            // Write transformed code
            launcher.prettyprint();
        }
        
        return transformationCount;
    }
    
    /**
     * Characterizes the breaking change for documentation purposes.
     */
    public static class BreakingChange {
        /** Old API pattern */
        public static final String OLD_API = "InvocationOnMock.getArgumentAt(int index, Class<T> clazz)";
        
        /** New API pattern */
        public static final String NEW_API = "InvocationOnMock.getArgument(int index, Class<T> clazz)";
        
        /** Structural transformation required */
        public static final String TRANSFORMATION = "Rename method from 'getArgumentAt' to 'getArgument' while preserving arguments";
        
        /** Mockito versions affected */
        public static final String AFFECTED_VERSIONS = "Projects upgrading from Mockito <5.0.0 to >=5.0.0";
        
        /** Description of the change */
        public static final String DESCRIPTION = 
            "In Mockito 5.0.0, the method getArgumentAt() was renamed to getArgument() " +
            "in the InvocationOnMock interface for consistency with other Mockito APIs.";
    }
}