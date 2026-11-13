package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.ArrayList;

/**
 * Generic JavaParser breaking change transformation tool.
 * This tool addresses common breaking changes in JavaParser dependency updates,
 * specifically focusing on ParseResult constructor changes that add CommentsCollection parameter.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDir);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();
        
        // Apply transformations for common JavaParser breaking changes
        applyJavaParserTransformations(model, factory);
        
        System.out.println("JavaParser transformation complete.");
    }
    
    /**
     * Apply generic transformations for common JavaParser breaking changes
     */
    private static void applyJavaParserTransformations(CtModel model, Factory factory) {
        // Handle ParseResult constructor changes - most common breaking change
        handleParseResultConstructorChanges(model, factory);
    }
    
    /**
     * Transform ParseResult constructor calls that are missing CommentsCollection parameter
     * This handles the breaking change where ParseResult constructor signature was modified
     * to include a CommentsCollection parameter
     */
    private static void handleParseResultConstructorChanges(CtModel model, Factory factory) {
        // Find all ParseResult constructor calls with exactly 2 arguments (old signature)
        List<CtNewClass> parseResultConstructors = model.getElements(
            new TypeFilter<>(CtNewClass.class) {
                @Override
                public boolean matches(CtNewClass element) {
                    // Match ParseResult constructor
                    return element.getType().getSimpleName().equals("ParseResult") &&
                           element.getArguments().size() == 2; // Old signature: (result, problems)
                }
            }
        );
        
        for (CtNewClass constructorCall : parseResultConstructors) {
            // Transform to include CommentsCollection parameter
            // The third parameter is CommentsCollection which should be null or default value
            List<CtExpression<?>> newArgs = new ArrayList<>(constructorCall.getArguments());
            
            // Add null for the new CommentsCollection parameter 
            // (in a real implementation, you might want to properly construct this)
            CtLiteral<?> nullLiteral = factory.createLiteral((Object) null);
            newArgs.add(nullLiteral);
            
            // Replace the constructor call arguments
            constructorCall.setArguments(newArgs);
            
            System.out.println("Updated ParseResult constructor call to include CommentsCollection parameter");
        }
        
        System.out.println("Found " + parseResultConstructors.size() + " ParseResult constructor calls to transform");
    }
}