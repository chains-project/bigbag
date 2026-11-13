package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;

/**
 * Generic Spoon transformation rule for fixing SnakeYAML 2.0 breaking changes.
 * 
 * This transformation addresses the breaking change where:
 * - org.yaml.snakeyaml.constructor.Constructor no longer has a no-argument constructor
 * - Constructor now requires a LoaderOptions parameter
 * 
 * Transformation: new Constructor() → new Constructor(new LoaderOptions())
 * 
 * This rule is generic and reusable for any project affected by this
 * SnakeYAML 2.0 breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Applies SnakeYAML 2.0 migration to fix Constructor() calls");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying SnakeYAML 2.0 migration to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        
        int transformationCount = 0;
        
        // Find all Constructor calls without arguments
        for (CtConstructorCall<?> constructorCall : launcher.getModel().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))) {
            try {
                // Check if this is a Constructor call from org.yaml.snakeyaml.constructor package
                if (constructorCall.getType() != null) {
                    String typeName = constructorCall.getType().getQualifiedName();
                    if ("org.yaml.snakeyaml.constructor.Constructor".equals(typeName)) {
                        // Check if it has no arguments
                        if (constructorCall.getArguments().isEmpty()) {
                            System.out.println("Found Constructor() call at: " + 
                                constructorCall.getPosition().getFile().getName() + 
                                ":" + constructorCall.getPosition().getLine());
                            
                            // Create new LoaderOptions constructor call
                            CtConstructorCall<?> loaderOptionsCall = launcher.getFactory().Code()
                                .createConstructorCall(launcher.getFactory().Type()
                                    .createReference("org.yaml.snakeyaml.LoaderOptions"));
                            
                            // Replace the existing constructor call with new Constructor(new LoaderOptions())
                            constructorCall.setArguments(java.util.Collections.singletonList(loaderOptionsCall));
                            
                            System.out.println("Transformed to: new Constructor(new LoaderOptions())");
                            transformationCount++;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing constructor call: " + e.getMessage());
                // Continue with next element
            }
        }
        
        System.out.println("\n=== SNAKEYAML 2.0 MIGRATION SUMMARY ===");
        System.out.println("Fixed " + transformationCount + " Constructor() calls by adding LoaderOptions parameter");
        
        if (transformationCount > 0) {
            // Output the transformed code
            System.out.println("\nWriting transformed files...");
            launcher.setSourceOutputDirectory(new File(sourceDir + "-transformed"));
            launcher.prettyprint();
            
            System.out.println("\nTransformation complete. Output in: " + sourceDir + "-transformed");
            System.out.println("\nADDITIONAL NOTES:");
            System.out.println("1. This fixes the Constructor() → Constructor(LoaderOptions) breaking change");
            System.out.println("2. You may also need to address:");
            System.out.println("   - Global tags (!!) are disabled by default in SnakeYAML 2.0");
            System.out.println("   - Consider configuring LoaderOptions or using TypeDescriptions");
            System.out.println("   - Yaml.loadAll() returns raw Iterable instead of Iterable<Object>");
        } else {
            System.out.println("No Constructor() transformations needed.");
        }
    }
}