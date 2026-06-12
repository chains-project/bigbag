package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule for snakeyaml 1.x to 2.1 migration.
 * Fixes the breaking change where TrustedTagInspector was removed.
 * 
 * Old API: new TrustedTagInspector()
 * New API: tag -> true (lambda implementing TagInspector interface)
 */
public class SnakeYamlTransformation {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying snakeyaml 2.1 TrustedTagInspector transformation to: " + sourceDirectory);
        
        try {
            // Create Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDirectory);
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setLevel("INFO");
            
            // Build model
            CtModel model = launcher.buildModel();
            
            int importCount = removeTrustedTagInspectorImports(model);
            int replacementCount = replaceTrustedTagInspectorInstances(model);
            
            if (importCount == 0 && replacementCount == 0) {
                System.out.println("No TrustedTagInspector usage found. Nothing to transform.");
                return;
            }
            
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDirectory);
            launcher.prettyprint();
            
            System.out.println("\nTransformation completed successfully!");
            System.out.println("Removed " + importCount + " TrustedTagInspector imports");
            System.out.println("Replaced " + replacementCount + " TrustedTagInspector instantiations with 'tag -> true'");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Remove imports of org.yaml.snakeyaml.inspector.TrustedTagInspector
     */
    private static int removeTrustedTagInspectorImports(CtModel model) {
        int count = 0;
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        
        for (CtImport ctImport : imports) {
            String importStr = ctImport.toString();
            if (importStr.contains("org.yaml.snakeyaml.inspector.TrustedTagInspector")) {
                try {
                    ctImport.delete();
                    count++;
                    System.out.println("Removed import: " + importStr);
                } catch (Exception e) {
                    System.err.println("Warning: Could not remove import: " + importStr + " - " + e.getMessage());
                }
            }
        }
        
        return count;
    }
    
    /**
     * Replace all instances of new TrustedTagInspector() with tag -> true
     */
    private static int replaceTrustedTagInspectorInstances(CtModel model) {
        int count = 0;
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            try {
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef != null) {
                    String typeName = typeRef.getQualifiedName();
                    if ("org.yaml.snakeyaml.inspector.TrustedTagInspector".equals(typeName)) {
                        System.out.println("Found TrustedTagInspector instantiation at: " + 
                            getPositionInfo(constructorCall));
                        
                        // Replace with lambda using code snippet (most reliable approach)
                        CtCodeSnippetExpression<Object> lambdaSnippet = 
                            constructorCall.getFactory().createCodeSnippetExpression("tag -> true");
                        
                        constructorCall.replace(lambdaSnippet);
                        count++;
                        
                        System.out.println("  Replaced with: tag -> true");
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not process constructor call at " + 
                    getPositionInfo(constructorCall) + " - " + e.getMessage());
            }
        }
        
        return count;
    }
    
    /**
     * Get position information for debugging
     */
    private static String getPositionInfo(CtElement element) {
        if (element.getPosition() != null && element.getPosition().isValidPosition()) {
            return element.getPosition().getFile().getName() + ":" + element.getPosition().getLine();
        }
        return "unknown position";
    }
}