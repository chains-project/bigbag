package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtImportKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix SnakeYAML 2.1 breaking change:
 * - Old API: new TrustedTagInspector()
 * - New API: tag -> true (lambda implementing TagInspector interface)
 * 
 * This transformation:
 * 1. Replaces all constructor calls to TrustedTagInspector with lambda expressions
 * 2. Removes imports of TrustedTagInspector
 * 
 * The transformation is generic and can be applied to any project affected by
 * this SnakeYAML API change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("\nApplies transformation for SnakeYAML 2.1 breaking change:");
            System.err.println("  Replaces: new TrustedTagInspector()");
            System.err.println("  With:     tag -> true");
            System.err.println("  Removes:  import org.yaml.snakeyaml.inspector.TrustedTagInspector;");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: SnakeYAML 2.1 TrustedTagInspector -> lambda");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            int replacements = fixTrustedTagInspectorCalls(model);
            int importsRemoved = removeTrustedTagInspectorImports(model);
            
            if (replacements > 0 || importsRemoved > 0) {
                launcher.prettyprint();
                System.out.println("Transformation completed successfully:");
                System.out.println("  - Replaced " + replacements + " TrustedTagInspector constructor calls");
                System.out.println("  - Removed " + importsRemoved + " TrustedTagInspector imports");
            } else {
                System.out.println("No TrustedTagInspector usage found.");
            }
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Finds and replaces all constructor calls to TrustedTagInspector with lambda expressions.
     * 
     * @param model The Spoon model of the codebase
     * @return Number of replacements made
     */
    private static int fixTrustedTagInspectorCalls(CtModel model) {
        List<CtConstructorCall<?>> constructorCalls = model
            .getElements(new TypeFilter<>(CtConstructorCall.class));
        
        int replacements = 0;
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            CtTypeReference<?> constructedType = constructorCall.getType();
            if (constructedType != null && 
                constructedType.getQualifiedName() != null &&
                constructedType.getQualifiedName().contains("TrustedTagInspector")) {
                
                System.out.println("  Found TrustedTagInspector constructor at: " + 
                    constructorCall.getPosition());
                
                Factory factory = constructorCall.getFactory();
                
                // Create lambda expression: tag -> true
                CtLambda<Boolean> lambda = factory.createLambda();
                
                // Create boolean literal true
                CtLiteral<Boolean> trueLiteral = factory.createLiteral(true);
                trueLiteral.setValue(true);
                lambda.setExpression(trueLiteral);
                
                // Replace the constructor call with lambda
                constructorCall.replace(lambda);
                replacements++;
            }
        }
        return replacements;
    }
    
    /**
     * Removes all imports of TrustedTagInspector.
     * 
     * @param model The Spoon model of the codebase
     * @return Number of imports removed
     */
    private static int removeTrustedTagInspectorImports(CtModel model) {
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        int importsRemoved = 0;
        for (CtImport ctImport : imports) {
            if (ctImport.getImportKind() == CtImportKind.TYPE) {
                try {
                    CtTypeReference<?> importedType = (CtTypeReference<?>) ctImport.getReference();
                    if (importedType != null && 
                        importedType.getQualifiedName() != null &&
                        importedType.getQualifiedName().contains("TrustedTagInspector")) {
                        
                        System.out.println("  Removing import: " + importedType.getQualifiedName());
                        ctImport.delete();
                        importsRemoved++;
                    }
                } catch (Exception e) {
                    // Skip if we can't process this import
                }
            }
        }
        return importsRemoved;
    }
}