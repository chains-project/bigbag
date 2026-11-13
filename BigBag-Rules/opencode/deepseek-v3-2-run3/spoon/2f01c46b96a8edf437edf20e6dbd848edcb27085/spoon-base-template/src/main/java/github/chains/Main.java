package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.replace.ReplacementVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * GENERIC Spoon transformation template for fixing breaking dependency updates.
 * 
 * This template demonstrates how to create a reusable transformation that fixes
 * the Xpp3Dom package migration from:
 *   org.codehaus.plexus.util.xml.Xpp3Dom 
 * to:
 *   org.apache.maven.shared.utils.xml.Xpp3Dom
 * 
 * PATTERN CHARACTERIZATION:
 * - Old API: org.codehaus.plexus.util.xml.Xpp3Dom (in plexus-utils 3.x)
 * - New API: org.apache.maven.shared.utils.xml.Xpp3Dom (in maven-shared-utils)
 * - Structural transformation: Update import statements and fully-qualified type references
 * - Scope: Any project using Xpp3Dom from plexus-utils needs this update when upgrading to plexus-utils 4.0.0
 * 
 * HOW TO ADAPT FOR OTHER BREAKING CHANGES:
 * 1. Update OLD_PACKAGE, OLD_TYPE_NAME, NEW_PACKAGE, NEW_TYPE_NAME constants
 * 2. The transformation will handle imports and fully-qualified references automatically
 * 3. Method references and other usage patterns may need additional handling
 */
public class Main {
    
    // CONFIGURATION PARAMETERS - ADJUST THESE FOR DIFFERENT BREAKING CHANGES
    private static final String OLD_PACKAGE = "org.codehaus.plexus.util.xml";
    private static final String OLD_TYPE_NAME = "Xpp3Dom";
    private static final String OLD_FULLY_QUALIFIED_NAME = OLD_PACKAGE + "." + OLD_TYPE_NAME;
    
    private static final String NEW_PACKAGE = "org.apache.maven.shared.utils.xml";
    private static final String NEW_TYPE_NAME = "Xpp3Dom";
    private static final String NEW_FULLY_QUALIFIED_NAME = NEW_PACKAGE + "." + NEW_TYPE_NAME;
    
    /**
     * Main entry point for the transformation.
     * 
     * @param args Command line arguments: <source-directory>
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Generic Dependency Fix Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Transformation: " + OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
        System.out.println("==============================================");
        
        try {
            // Initialize Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(true); // Don't require dependencies
            launcher.getEnvironment().setAutoImports(true); // Preserve import statements
            launcher.getEnvironment().setCommentEnabled(true); // Preserve comments
            launcher.getEnvironment().setPreserveLineNumbers(true); // Preserve line numbers
            
            // Build the AST model
            CtModel model = launcher.buildModel();
            
            // Track transformation statistics
            int importsUpdated = 0;
            int typeRefsUpdated = 0;
            
            // PHASE 1: Update import statements
            System.out.println("\nPhase 1: Scanning for imports...");
            List<CtImport> importsToChange = new ArrayList<>();
            
            for (CtImport ctImport : model.getRootPackage().getElements(new TypeFilter<CtImport>(CtImport.class))) {
                String imported = ctImport.getReference().toString();
                if (imported.equals(OLD_FULLY_QUALIFIED_NAME)) {
                    importsToChange.add(ctImport);
                }
            }
            
            for (CtImport imp : importsToChange) {
                try {
                    // Create new import reference
                    CtTypeReference<?> newRef = imp.getFactory().createReference(NEW_FULLY_QUALIFIED_NAME);
                    imp.setReference(newRef);
                    importsUpdated++;
                    System.out.println("  Updated import statement");
                } catch (Exception e) {
                    System.err.println("  Warning: Failed to update import: " + e.getMessage());
                }
            }
            
            // PHASE 2: Update fully-qualified type references in code
            System.out.println("\nPhase 2: Scanning for type references...");
            List<CtTypeReference<?>> typeRefsToChange = new ArrayList<>();
            
            for (CtTypeReference<?> typeRef : model.getRootPackage().getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
                String typeName = typeRef.getQualifiedName();
                if (typeName.equals(OLD_FULLY_QUALIFIED_NAME)) {
                    typeRefsToChange.add(typeRef);
                }
            }
            
            for (CtTypeReference<?> typeRef : typeRefsToChange) {
                try {
                    // Update the type reference
                    CtTypeReference<?> newTypeRef = typeRef.getFactory().createReference(NEW_FULLY_QUALIFIED_NAME);
                    
                    // Copy any type arguments
                    if (typeRef.getActualTypeArguments() != null && !typeRef.getActualTypeArguments().isEmpty()) {
                        newTypeRef.setActualTypeArguments(typeRef.getActualTypeArguments());
                    }
                    
                    // Replace the reference in the AST
                    typeRef.replace(newTypeRef);
                    typeRefsUpdated++;
                    System.out.println("  Updated type reference at: " + typeRef.getPosition());
                } catch (Exception e) {
                    System.err.println("  Warning: Failed to update type reference: " + e.getMessage());
                }
            }
            
            // PHASE 3: Apply transformations and write output
            System.out.println("\nPhase 3: Applying transformations...");
            launcher.setSourceOutputDirectory(sourceDir); // Overwrite source files
            launcher.prettyprint();
            
            // SUMMARY
            System.out.println("\n=== Transformation Summary ===");
            System.out.println("Import statements updated: " + importsUpdated);
            System.out.println("Type references updated: " + typeRefsUpdated);
            System.out.println("Total changes: " + (importsUpdated + typeRefsUpdated));
            
            if (importsUpdated == 0 && typeRefsUpdated == 0) {
                System.out.println("\nNo changes were made. Possible reasons:");
                System.out.println("1. The code doesn't use " + OLD_FULLY_QUALIFIED_NAME);
                System.out.println("2. The code already uses " + NEW_FULLY_QUALIFIED_NAME);
                System.out.println("3. The code uses simple class name (Xpp3Dom) without imports");
            }
            
            System.out.println("\nTransformation completed successfully!");
            
        } catch (Exception e) {
            System.err.println("\nERROR during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Example of how to extend this transformation for more complex patterns.
     * This method shows how to handle method references like Xpp3Dom::getValue
     */
    private void handleMethodReferences() {
        // For method references, you would need to:
        // 1. Find method references (CtExecutableReference)
        // 2. Check if they reference methods from the old type
        // 3. Update them to reference the new type
        
        // Example pattern to look for:
        // - Xpp3Dom::getChildren
        // - Xpp3Dom::getValue
        // - etc.
        
        // This requires more advanced Spoon usage and is left as an exercise
        // for specific breaking changes that affect method signatures.
    }
}