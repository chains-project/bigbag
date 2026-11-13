package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule for HtmlUnit 2.x to 3.x migration.
 * Changes all references from com.gargoylesoftware.htmlunit to org.htmlunit.
 * This is a generic rule that can be applied to any project affected by this breaking change.
 * 
 * Characterized breaking change:
 * - Old API pattern: com.gargoylesoftware.htmlunit.ScriptResult
 * - New API pattern: org.htmlunit.ScriptResult
 * - Structural transformation: Simple package rename
 * - Method signatures remain identical
 */
public class Main {
    
    // Configuration parameters - can be modified for different breaking changes
    private static final String OLD_PACKAGE = "com.gargoylesoftware.htmlunit";
    private static final String NEW_PACKAGE = "org.htmlunit";
    private static final String CLASS_NAME = "ScriptResult";
    private static final String OLD_FULLY_QUALIFIED_NAME = OLD_PACKAGE + "." + CLASS_NAME;
    private static final String NEW_FULLY_QUALIFIED_NAME = NEW_PACKAGE + "." + CLASS_NAME;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.err.println("\nThis transformation fixes the HtmlUnit 2.x to 3.x breaking change:");
            System.err.println("  " + OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Applying generic API migration transformation...");
        System.out.println("Source directory: " + sourcePath);
        System.out.println("Transformation pattern: " + OLD_FULLY_QUALIFIED_NAME + " -> " + NEW_FULLY_QUALIFIED_NAME);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(false);
            launcher.addInputResource(sourcePath);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            int changesMade = 0;
            
            // Get all elements from the model root
            List<CtImport> imports = model.getRootPackage().getElements(new TypeFilter<CtImport>(CtImport.class) {});
            for (CtImport ctImport : imports) {
                String importString = ctImport.toString();
                if (importString.contains(OLD_FULLY_QUALIFIED_NAME)) {
                    System.out.println("Found import: " + importString);
                    // Create new import reference
                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(NEW_FULLY_QUALIFIED_NAME);
                    
                    // Delete old import and create new one
                    ctImport.delete();
                    launcher.getFactory().createImport(newTypeRef);
                    System.out.println("  -> Updated to: import " + NEW_FULLY_QUALIFIED_NAME);
                    changesMade++;
                }
            }
            
            // Update type references
            List<CtTypeReference<?>> typeRefs = model.getRootPackage().getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {});
            for (CtTypeReference<?> typeRef : typeRefs) {
                String qualifiedName = typeRef.getQualifiedName();
                if (qualifiedName != null && qualifiedName.equals(OLD_FULLY_QUALIFIED_NAME)) {
                    System.out.println("Found type reference: " + qualifiedName + " at " + typeRef.getPosition());
                    // Replace with new type reference
                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(NEW_FULLY_QUALIFIED_NAME);
                    typeRef.replace(newTypeRef);
                    changesMade++;
                }
            }
            
            // Update constructor calls
            List<CtConstructorCall<?>> constructorCalls = model.getRootPackage().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {});
            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                CtTypeReference<?> constructedType = constructorCall.getType();
                if (constructedType != null && OLD_FULLY_QUALIFIED_NAME.equals(constructedType.getQualifiedName())) {
                    System.out.println("Found constructor call: new " + OLD_FULLY_QUALIFIED_NAME + "(...) at " + constructorCall.getPosition());
                    // Update the type reference in the constructor call
                    CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(NEW_FULLY_QUALIFIED_NAME);
                    constructorCall.setType(newTypeRef);
                    changesMade++;
                }
            }
            
            System.out.println("\nTransformation complete. Total changes made: " + changesMade);
            
            if (changesMade > 0) {
                // Output the transformed code
                launcher.setSourceOutputDirectory("./spoon-output");
                launcher.prettyprint();
                System.out.println("Transformed code written to ./spoon-output");
                
                // Also write a summary of the transformation
                System.out.println("\n=== Transformation Summary ===");
                System.out.println("Breaking change fixed: HtmlUnit 2.x to 3.x package migration");
                System.out.println("Old API: " + OLD_FULLY_QUALIFIED_NAME);
                System.out.println("New API: " + NEW_FULLY_QUALIFIED_NAME);
                System.out.println("This is a generic transformation that can be applied to any project");
                System.out.println("affected by the same breaking change by modifying the configuration");
                System.out.println("parameters at the top of the Main.java file.");
            } else {
                System.out.println("No changes were needed - project already uses the new API.");
            }
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Helper method to demonstrate how to adapt this transformation for other breaking changes.
     * To use this transformation for a different API migration:
     * 1. Change OLD_PACKAGE and NEW_PACKAGE constants
     * 2. Change CLASS_NAME constant if needed
     * 3. Recompile and run
     */
    public static void demonstrateAdaptability() {
        System.out.println("=== Example adaptations ===");
        System.out.println("For com.old.package.Foo -> com.new.package.Foo:");
        System.out.println("  Set OLD_PACKAGE = \"com.old.package\"");
        System.out.println("  Set NEW_PACKAGE = \"com.new.package\"");
        System.out.println("  Set CLASS_NAME = \"Foo\"");
        System.out.println("");
        System.out.println("For javax.xml.bind -> jakarta.xml.bind (Java EE to Jakarta EE):");
        System.out.println("  Set OLD_PACKAGE = \"javax.xml.bind\"");
        System.out.println("  Set NEW_PACKAGE = \"jakarta.xml.bind\"");
        System.out.println("  Set CLASS_NAME = \"JAXBContext\" (or use generic approach for all classes)");
    }
}