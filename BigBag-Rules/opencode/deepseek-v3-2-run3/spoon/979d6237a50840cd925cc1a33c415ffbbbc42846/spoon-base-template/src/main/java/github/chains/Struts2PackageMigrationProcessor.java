package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtJavaDocTag;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * A Spoon processor that fixes the Struts2 package migration from
 * org.apache.struts2.dispatcher.ng.filter to org.apache.struts2.dispatcher.filter
 * for StrutsPrepareAndExecuteFilter class.
 * 
 * This is a generic transformation that can be applied to any project
 * affected by this breaking change in Struts2 2.5.30.
 */
public class Struts2PackageMigrationProcessor extends AbstractProcessor<CtType<?>> {
    
    private static final String OLD_PACKAGE = "org.apache.struts2.dispatcher.ng.filter";
    private static final String NEW_PACKAGE = "org.apache.struts2.dispatcher.filter";
    private static final String CLASS_NAME = "StrutsPrepareAndExecuteFilter";
    private static final String OLD_FULL_NAME = OLD_PACKAGE + "." + CLASS_NAME;
    private static final String NEW_FULL_NAME = NEW_PACKAGE + "." + CLASS_NAME;
    
    private int importChanges = 0;
    private int typeRefChanges = 0;
    
    @Override
    public void process(CtType<?> type) {
        // Process imports in the compilation unit containing this type
        CtCompilationUnit cu = type.getPosition().getCompilationUnit();
        if (cu != null) {
            processImports(cu);
        }
        
        // Process all type references in this type
        processTypeReferences(type);
    }
    
    private void processImports(CtCompilationUnit cu) {
        List<CtImport> imports = cu.getImports();
        for (CtImport imp : imports) {
            String importStr = imp.toString();
            if (importStr.contains(OLD_FULL_NAME)) {
                System.out.println("Found import to fix: " + importStr);
                
                // Create new import
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(NEW_FULL_NAME);
                
                // Note: In Spoon, imports are tricky to modify directly
                // A better approach is to let auto-imports handle it after we fix type references
                // So we'll just track this and fix the type references
                importChanges++;
            }
        }
    }
    
    private void processTypeReferences(CtType<?> type) {
        // Find all type references in this type's AST
        List<CtTypeReference<?>> typeRefs = type.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> ref) {
                String qualifiedName = ref.getQualifiedName();
                return qualifiedName != null && qualifiedName.equals(OLD_FULL_NAME);
            }
        });
        
        for (CtTypeReference<?> ref : typeRefs) {
            System.out.println("Fixing type reference in " + type.getQualifiedName() + ": " + ref.getQualifiedName());
            
            // Create new type reference
            CtTypeReference<?> newRef = getFactory().Type().createReference(NEW_FULL_NAME);
            
            // Copy important properties
            newRef.setImplicit(ref.isImplicit());
            
            // Replace the old reference
            ref.replace(newRef);
            typeRefChanges++;
        }
    }
    
    public int getImportChanges() {
        return importChanges;
    }
    
    public int getTypeRefChanges() {
        return typeRefChanges;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.Struts2PackageMigrationProcessor <source-directory>");
            System.err.println("Applies Struts2 2.5.30 package migration transformation");
            System.err.println("Transformation: " + OLD_FULL_NAME + " -> " + NEW_FULL_NAME);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = sourceDir + "-transformed";
        
        System.out.println("=== Struts2 Package Migration Transformation ===");
        System.out.println("Source: " + sourceDir);
        System.out.println("Output: " + outputDir);
        System.out.println("Transformation: " + OLD_FULL_NAME + " -> " + NEW_FULL_NAME);
        System.out.println();
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setLevel("OFF"); // Reduce noise
            
            // Create and add our processor
            Struts2PackageMigrationProcessor processor = new Struts2PackageMigrationProcessor();
            launcher.addProcessor(processor);
            
            // Run the transformation
            launcher.run();
            
            // Print summary
            System.out.println();
            System.out.println("=== Transformation Summary ===");
            System.out.println("Import statements found: " + processor.getImportChanges());
            System.out.println("Type references fixed: " + processor.getTypeRefChanges());
            
            if (processor.getImportChanges() > 0 || processor.getTypeRefChanges() > 0) {
                // Write transformed code
                launcher.setSourceOutputDirectory(outputDir);
                launcher.prettyprint();
                System.out.println();
                System.out.println("Success! Transformed code written to: " + outputDir);
                System.out.println();
                System.out.println("To apply these changes to your project:");
                System.out.println("1. Review changes in " + outputDir);
                System.out.println("2. Copy modified files back to original location");
                System.out.println("3. Recompile your project");
            } else {
                System.out.println();
                System.out.println("No changes needed. Code already uses the new package structure.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}