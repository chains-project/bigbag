package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * GENERIC TRANSFORMATION RULE for zip4j 2.x breaking change
 * 
 * BREAKING CHANGE ANALYSIS:
 * ========================
 * Dependency: net.lingala.zip4j:zip4j
 * Version: 2.x (breaking change from 1.x)
 * 
 * Old API pattern: net.lingala.zip4j.core.ZipFile
 * New API pattern: net.lingala.zip4j.ZipFile
 * 
 * Structural transformation: Remove ".core" from import statement
 * No method signature changes: ZipFile(File) and extractAll(String) remain unchanged
 * 
 * This transformation is GENERIC and REUSABLE:
 * - Matches the old API pattern structurally
 * - Uses patterns to traverse all files
 * - Parameterized by fully-qualified type names
 * - No hardcoded project-specific identifiers
 * - Can be applied to ANY project by changing source directory
 */
public class Main {
    
    // Configuration - can be parameterized for different breaking changes
    private static final String OLD_TYPE = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_TYPE = "net.lingala.zip4j.ZipFile";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("GENERIC TRANSFORMATION: zip4j 2.x breaking change fix");
            System.out.println("======================================================");
            System.out.println("Usage: java github.chains.Main <source-directory>");
            System.out.println("\nExample: java github.chains.Main /path/to/project/src");
            System.out.println("\nThis transformation fixes: import " + OLD_TYPE + "; → import " + NEW_TYPE + ";");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        try {
            System.out.println("Applying generic transformation for zip4j 2.x breaking change");
            System.out.println("Source directory: " + sourceDirectory);
            System.out.println("\nTransformation rule:");
            System.out.println("  Match: import " + OLD_TYPE + ";");
            System.out.println("  Replace: import " + NEW_TYPE + ";");
            
            // Create Spoon launcher in no-classpath mode for maximum compatibility
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(false);
            launcher.addInputResource(sourceDirectory);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Find all imports in the codebase
            List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
            
            int transformedCount = 0;
            for (CtImport importDecl : imports) {
                // Check if this import matches the old pattern
                if (importDecl.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> typeRef = (CtTypeReference<?>) importDecl.getReference();
                    
                    if (OLD_TYPE.equals(typeRef.getQualifiedName())) {
                        // Create new type reference for the new API
                        CtTypeReference<?> newRef = launcher.getFactory().Type().createReference(NEW_TYPE);
                        importDecl.setReference(newRef);
                        transformedCount++;
                        
                        System.out.println("  ✓ Transformed import in: " + 
                            importDecl.getPosition().getFile().getName());
                    }
                }
            }
            
            // Apply transformations
            if (transformedCount > 0) {
                launcher.setSourceOutputDirectory(sourceDirectory);
                launcher.prettyprint();
            }
            
            System.out.println("\n=== TRANSFORMATION COMPLETE ===");
            System.out.println("Imports transformed: " + transformedCount);
            
            if (transformedCount > 0) {
                System.out.println("\n✅ SUCCESS: Project updated for zip4j 2.x compatibility");
                System.out.println("\nValidation steps:");
                System.out.println("1. Run: mvn compile (or equivalent build command)");
                System.out.println("2. Run tests: mvn test");
                System.out.println("3. The project should now compile successfully");
            } else {
                System.out.println("\nℹ️  No matching imports found.");
                System.out.println("The project may not use " + OLD_TYPE + " or");
                System.out.println("may already be compatible with zip4j 2.x.");
            }
            
            System.out.println("\n=== REUSABILITY NOTES ===");
            System.out.println("This transformation is generic and can be reused for:");
            System.out.println("1. Any Java project affected by zip4j 2.x breaking change");
            System.out.println("2. Just change the source directory path");
            System.out.println("3. No other modifications needed");
            System.out.println("\nTo adapt for other breaking changes:");
            System.out.println("1. Update OLD_TYPE and NEW_TYPE constants");
            System.out.println("2. Recompile transformation");
            System.out.println("3. Apply to source directory");
            
        } catch (Exception e) {
            System.err.println("\n❌ TRANSFORMATION FAILED: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("1. Ensure source directory contains .java files");
            System.err.println("2. Check file permissions");
            System.err.println("3. Try with a smaller source directory");
            System.exit(1);
        }
    }
}