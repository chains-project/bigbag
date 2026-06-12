package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation for fixing package relocation breaking changes.
 * 
 * This transformation handles the case where a class has been moved from one package to another.
 * It fixes:
 * 1. Import statements referencing the old package
 * 2. Fully-qualified type references in code
 * 3. Type references in variable declarations, method parameters, etc.
 * 
 * To use this transformation for a different package relocation:
 * 1. Update OLD_PACKAGE, NEW_PACKAGE, and CLASS_NAME constants
 * 2. Recompile and run on the target source directory
 * 
 * Breaking Change Characterization:
 * - Old API pattern: org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext
 * - New API pattern: org.apache.maven.doxia.siterenderer.RenderingContext
 * - Structural transformation: Package name change only, class name and constructor signatures remain same
 */
public class Main {
    // Configuration parameters - customize these for different breaking changes
    private static final String OLD_PACKAGE = "org.apache.maven.doxia.module.xhtml.decoration.render";
    private static final String NEW_PACKAGE = "org.apache.maven.doxia.siterenderer";
    private static final String CLASS_NAME = "RenderingContext";
    private static final String OLD_FULLY_QUALIFIED_NAME = OLD_PACKAGE + "." + CLASS_NAME;
    private static final String NEW_FULLY_QUALIFIED_NAME = NEW_PACKAGE + "." + CLASS_NAME;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Configuration (update these constants for different breaking changes):");
            System.err.println("  OLD_PACKAGE: " + OLD_PACKAGE);
            System.err.println("  NEW_PACKAGE: " + NEW_PACKAGE);
            System.err.println("  CLASS_NAME: " + CLASS_NAME);
            System.err.println("\nThis transformation fixes package relocation breaking changes where:");
            System.err.println("  - A class moves from OLD_PACKAGE to NEW_PACKAGE");
            System.err.println("  - Class name remains: " + CLASS_NAME);
            System.err.println("  - API signatures remain compatible");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Package Relocation Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Transformation: " + OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
        System.out.println("\nBreaking Change Pattern:");
        System.out.println("  - Old package: " + OLD_PACKAGE);
        System.out.println("  - New package: " + NEW_PACKAGE);
        System.out.println("  - Class name: " + CLASS_NAME + " (unchanged)");
        System.out.println("  - Constructor/method signatures: Compatible");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        
        // Add our processor to handle the transformation
        launcher.addProcessor(new PackageRelocationProcessor());
        
        try {
            // Build and process the model
            launcher.run();
            
            System.out.println("\n✅ Transformation complete!");
            System.out.println("\nSummary:");
            System.out.println("  - Fixed import statements");
            System.out.println("  - Updated type references in code");
            System.out.println("  - Package: " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
            System.out.println("  - Class: " + CLASS_NAME + " (unchanged)");
            System.out.println("\n📋 This is a GENERIC transformation that can be reused for any similar");
            System.out.println("   package relocation breaking change by updating the configuration constants.");
            System.out.println("\n🔧 To adapt for another breaking change:");
            System.out.println("   1. Update OLD_PACKAGE, NEW_PACKAGE, and CLASS_NAME constants");
            System.out.println("   2. Recompile: mvn clean package");
            System.out.println("   3. Run: java -jar target/spoon-base-1.0-SNAPSHOT.jar <source-dir>");
            
        } catch (Exception e) {
            System.err.println("\n❌ Transformation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Spoon processor that handles package relocation transformations.
     * This is the core transformation logic that can be reused for any
     * package relocation breaking change by updating the configuration constants.
     */
    static class PackageRelocationProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeRef) {
            // Check if this type reference matches our old fully qualified name
            if (OLD_FULLY_QUALIFIED_NAME.equals(typeRef.getQualifiedName())) {
                System.out.println("  Fixing type reference at " + typeRef.getPosition() + ": " + 
                                 OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
                
                // Create a new type reference with the updated package
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(NEW_FULLY_QUALIFIED_NAME);
                
                // Replace the old type reference with the new one
                typeRef.replace(newTypeRef);
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            // Only process type references that match our old fully qualified name
            // This pattern matching ensures we only transform the exact class that was relocated
            return OLD_FULLY_QUALIFIED_NAME.equals(candidate.getQualifiedName());
        }
    }
}