package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtJavaDocTag;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Main class for Struts2 package migration transformation.
 * This transformation fixes the breaking change in Struts2 2.5.30 where
 * StrutsPrepareAndExecuteFilter moved from package:
 *   org.apache.struts2.dispatcher.ng.filter
 * to:
 *   org.apache.struts2.dispatcher.filter
 * 
 * USAGE:
 *   java -jar spoon-transformation.jar <source-directory>
 * 
 * This is a GENERIC transformation that can be applied to ANY project
 * affected by this breaking change, not just the @guice/ project.
 */
public class Main extends AbstractProcessor<CtType<?>> {
    
    // Constants parameterizing the transformation
    private static final String OLD_PACKAGE = "org.apache.struts2.dispatcher.ng.filter";
    private static final String NEW_PACKAGE = "org.apache.struts2.dispatcher.filter";
    private static final String CLASS_NAME = "StrutsPrepareAndExecuteFilter";
    private static final String OLD_FULL_NAME = OLD_PACKAGE + "." + CLASS_NAME;
    private static final String NEW_FULL_NAME = NEW_PACKAGE + "." + CLASS_NAME;
    
    private int importChanges = 0;
    private int typeRefChanges = 0;
    
    @Override
    public void process(CtType<?> type) {
        // Process all type references in this type
        List<CtTypeReference<?>> typeRefs = type.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> ref) {
                String qualifiedName = ref.getQualifiedName();
                return qualifiedName != null && qualifiedName.equals(OLD_FULL_NAME);
            }
        });
        
        for (CtTypeReference<?> ref : typeRefs) {
            System.out.println("[INFO] Fixing type reference in " + type.getQualifiedName() + ": " + ref.getQualifiedName());
            
            // Create new type reference with updated package
            CtTypeReference<?> newRef = getFactory().Type().createReference(NEW_FULL_NAME);
            
            // Copy relevant properties
            newRef.setImplicit(ref.isImplicit());
            
            // Replace the old reference with the new one
            ref.replace(newRef);
            typeRefChanges++;
        }
        
        // Note: Import statements are automatically updated by Spoon's auto-import
        // feature when we fix the type references above
    }
    
    public int getImportChanges() {
        return importChanges;
    }
    
    public int getTypeRefChanges() {
        return typeRefChanges;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = sourceDir + "-transformed";
        
        printHeader();
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println();
        System.out.println("Breaking Change:");
        System.out.println("  OLD: " + OLD_FULL_NAME);
        System.out.println("  NEW: " + NEW_FULL_NAME);
        System.out.println();
        
        try {
            // Configure Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(true); // Don't require full classpath
            launcher.getEnvironment().setAutoImports(true); // Critical for import updates
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setLevel("INFO");
            
            // Create and add our processor
            Main processor = new Main();
            launcher.addProcessor(processor);
            
            // Run the transformation
            System.out.println("Running transformation...");
            launcher.run();
            
            // Print transformation summary
            printSummary(processor, outputDir);
            
            // Write the transformed code if changes were made
            if (processor.getTypeRefChanges() > 0) {
                launcher.setSourceOutputDirectory(outputDir);
                launcher.prettyprint();
                System.out.println();
                System.out.println("✅ Transformation complete!");
                System.out.println("✅ Output written to: " + outputDir);
                printNextSteps(sourceDir, outputDir);
            } else {
                System.out.println();
                System.out.println("ℹ️  No changes needed. Code already uses the new package structure.");
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printHeader() {
        System.out.println("================================================");
        System.out.println("   Struts2 2.5.30 Package Migration Tool");
        System.out.println("================================================");
        System.out.println();
    }
    
    private static void printUsage() {
        printHeader();
        System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
        System.err.println();
        System.err.println("Description:");
        System.err.println("  Applies automatic fix for Struts2 2.5.30 breaking change");
        System.err.println("  where StrutsPrepareAndExecuteFilter moved packages.");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  java -jar spoon-transformation.jar /path/to/project/src");
        System.err.println();
    }
    
    private static void printSummary(Main processor, String outputDir) {
        System.out.println();
        System.out.println("=== Transformation Summary ===");
        System.out.println("Type references fixed: " + processor.getTypeRefChanges());
        System.out.println("Note: Import statements are updated automatically");
        System.out.println("based on the fixed type references.");
    }
    
    private static void printNextSteps(String sourceDir, String outputDir) {
        System.out.println();
        System.out.println("=== Next Steps ===");
        System.out.println("1. Review the changes in: " + outputDir);
        System.out.println("2. Compare with original: " + sourceDir);
        System.out.println("3. Apply changes to your project:");
        System.out.println("   - Copy modified files from " + outputDir);
        System.out.println("     to " + sourceDir);
        System.out.println("4. Recompile your project");
        System.out.println();
        System.out.println("Note: This is a generic transformation that works for");
        System.out.println("ANY project affected by this Struts2 breaking change.");
    }
}