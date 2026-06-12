package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;

import java.util.List;

/**
 * Generic Spoon transformation for Struts2 package change.
 * This transformation fixes the breaking change in Struts2 2.5+ where:
 * - Package changed from: org.apache.struts2.dispatcher.ng.filter
 * - To: org.apache.struts2.dispatcher.filter
 * 
 * The transformation is fully generic and reusable:
 * 1. Works on any source directory
 * 2. Transforms all occurrences of the old type
 * 3. Preserves code structure and formatting
 * 4. Handles both imports and type references
 */
public class Main {
    
    // The breaking change configuration
    private static final String OLD_PACKAGE = "org.apache.struts2.dispatcher.ng.filter";
    private static final String NEW_PACKAGE = "org.apache.struts2.dispatcher.filter";
    private static final String CLASS_NAME = "StrutsPrepareAndExecuteFilter";
    
    private static final String OLD_FULL_TYPE = OLD_PACKAGE + "." + CLASS_NAME;
    private static final String NEW_FULL_TYPE = NEW_PACKAGE + "." + CLASS_NAME;
    
    public static void main(String[] args) {
        // Validate arguments
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        System.out.println("=== GENERIC STRUTS2 PACKAGE TRANSFORMATION ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Transformation:   " + OLD_FULL_TYPE);
        System.out.println("               -> " + NEW_FULL_TYPE);
        System.out.println();
        
        try {
            int changes = transformPackageReferences(sourceDir);
            
            System.out.println();
            if (changes > 0) {
                System.out.println("✅ SUCCESS: Applied " + changes + " transformation(s)");
            } else {
                System.out.println("ℹ️  INFO: No transformations needed");
                System.out.println("   (Source doesn't contain " + OLD_FULL_TYPE + ")");
            }
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("❌ ERROR: Transformation failed");
            System.err.println("   " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("   Cause: " + e.getCause().getMessage());
            }
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("GENERIC STRUTS2 PACKAGE TRANSFORMATION");
        System.err.println("=======================================");
        System.err.println();
        System.err.println("Fixes the Struts2 2.5+ breaking change where the package");
        System.err.println("changed from '.ng.filter' to '.filter'.");
        System.err.println();
        System.err.println("USAGE:");
        System.err.println("  java github.chains.Main <source-directory>");
        System.err.println();
        System.err.println("EXAMPLE:");
        System.err.println("  java github.chains.Main /workspace/project/src");
        System.err.println("  java github.chains.Main /workspace/guice");
        System.err.println();
        System.err.println("TRANSFORMATION:");
        System.err.println("  " + OLD_FULL_TYPE);
        System.err.println("  -> " + NEW_FULL_TYPE);
    }
    
    private static int transformPackageReferences(String sourceDir) {
        // Initialize Spoon
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build the AST
        System.out.println("Building AST model...");
        CtModel model = launcher.buildModel();
        
        // Count transformations
        int transformationCount = 0;
        
        // Get the factory from the model
        var factory = model.getRootPackage().getFactory();
        
        // Find all type references to the old class
        System.out.println("Scanning for type references...");
        List<CtTypeReference<?>> oldTypeRefs = Query.getElements(factory, 
            new Filter<CtTypeReference<?>>() {
                @Override
                public boolean matches(CtTypeReference<?> element) {
                    if (element == null) return false;
                    String qualifiedName = element.getQualifiedName();
                    return OLD_FULL_TYPE.equals(qualifiedName);
                }
            }
        );
        
        // Replace each found reference
        for (CtTypeReference<?> oldRef : oldTypeRefs) {
            // Get context for logging
            String context = getContext(oldRef);
            System.out.println("  Found in: " + context);
            
            // Create new reference
            CtTypeReference<?> newRef = factory.createReference(NEW_FULL_TYPE);
            
            // Preserve type arguments if any
            if (oldRef.getActualTypeArguments() != null && !oldRef.getActualTypeArguments().isEmpty()) {
                newRef.setActualTypeArguments(oldRef.getActualTypeArguments());
            }
            
            // Replace
            oldRef.replace(newRef);
            transformationCount++;
        }
        
        // Write changes if any
        if (transformationCount > 0) {
            System.out.println("Writing transformed code...");
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
        }
        
        return transformationCount;
    }
    
    private static String getContext(CtTypeReference<?> typeRef) {
        try {
            // Try to get the containing type
            CtType<?> parentType = typeRef.getParent(CtType.class);
            if (parentType != null) {
                return parentType.getQualifiedName();
            }
            
            // Fallback to position info
            var pos = typeRef.getPosition();
            if (pos != null && pos.getFile() != null) {
                return pos.getFile().getName();
            }
            
            return "unknown location";
            
        } catch (Exception e) {
            return "unknown context";
        }
    }
}