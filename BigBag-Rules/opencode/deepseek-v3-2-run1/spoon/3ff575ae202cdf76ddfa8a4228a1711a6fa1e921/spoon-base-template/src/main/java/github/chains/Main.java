package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

/**
 * Generic Spoon transformation to fix breaking API changes where
 * a method has been moved from a parent interface/class to a child interface/class.
 * 
 * Pattern: Fixes method calls like `expression.method()` where:
 * - `expression` returns type `SOURCE_TYPE`
 * - `method()` has been moved to `TARGET_TYPE` 
 * - Transformation: `((TARGET_TYPE) expression).method()`
 * 
 * Example: PlexusContainer.getLoggerManager() -> MutablePlexusContainer.getLoggerManager()
 * Configuration can be changed for similar breaking changes.
 */
public class Main extends AbstractProcessor<CtInvocation<?>> {
    
    // CONFIGURATION - Change these for different breaking changes
    private static final String SOURCE_TYPE = "org.codehaus.plexus.PlexusContainer";
    private static final String TARGET_TYPE = "org.codehaus.plexus.MutablePlexusContainer";
    private static final String MOVED_METHOD_NAME = "getLoggerManager";
    
    @Override
    public void process(CtInvocation<?> invocation) {
        // Check if this is a method invocation of the moved method
        if (!MOVED_METHOD_NAME.equals(invocation.getExecutable().getSimpleName())) {
            return;
        }
        
        // Get the target expression (e.g., getContainer() in getContainer().getLoggerManager())
        if (invocation.getTarget() == null) {
            return;
        }
        
        // In no-classpath mode, we can't check types precisely
        // We'll apply transformation to all matching method names
        // This is conservative but safe for the specific breaking change
        
        // Create a cast expression: (TARGET_TYPE) target
        CtTypeReference<?> targetTypeRef = getFactory().Type().createReference(TARGET_TYPE);
        
        // Clone the target and add type cast to it
        spoon.reflect.code.CtExpression<?> targetExpr = invocation.getTarget().clone();
        targetExpr.addTypeCast(targetTypeRef);
        
        // Replace the target with the cast expression
        invocation.setTarget(targetExpr);
        
        getEnvironment().reportProgressMessage(
            "Fixed: " + SOURCE_TYPE + "." + MOVED_METHOD_NAME + 
            " -> (" + TARGET_TYPE + ")expression." + MOVED_METHOD_NAME
        );
    }
    
    @Override
    public boolean isToBeProcessed(CtInvocation<?> candidate) {
        // Early filtering for performance
        return MOVED_METHOD_NAME.equals(candidate.getExecutable().getSimpleName());
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory> [output-directory]");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.err.println("\nConfiguration (edit source to change):");
            System.err.println("  SOURCE_TYPE: " + SOURCE_TYPE);
            System.err.println("  TARGET_TYPE: " + TARGET_TYPE);
            System.err.println("  MOVED_METHOD_NAME: " + MOVED_METHOD_NAME);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir + "-transformed";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true); // Use no classpath to avoid dependency issues
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add our processor
        launcher.addProcessor(new Main());
        
        try {
            System.out.println("Running generic transformation...");
            System.out.println("Pattern: " + SOURCE_TYPE + "." + MOVED_METHOD_NAME + 
                             " -> (" + TARGET_TYPE + ")expression." + MOVED_METHOD_NAME);
            System.out.println("Processing directory: " + sourceDir);
            System.out.println("Output directory: " + outputDir);
            
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Modified files saved to: " + outputDir);
            System.out.println("\nNote: To apply to different breaking changes, edit SOURCE_TYPE, TARGET_TYPE, and MOVED_METHOD_NAME constants.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}