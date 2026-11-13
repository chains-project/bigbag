package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.processing.AbstractProcessor;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation to fix Hamcrest StringContains constructor calls.
 * 
 * This transformation addresses the breaking change in Hamcrest library where
 * StringContains constructor changed from (boolean, String) to (String).
 * 
 * The transformation identifies calls to StringContains constructors with boolean
 * as the first argument and removes it, keeping only the string argument.
 */
public class Main {
    public static void main(String[] args) {
        // Set up Spoon
        Launcher launcher = new Launcher();
        
        // Add source directory  
        launcher.addInputResource("/workspace/docker-adapter/src/test");
        
        // Set output directory
        launcher.setSourceOutputDirectory("/workspace/docker-adapter/src/test-fixed");

        // Process with our custom processor
        launcher.addProcessor(new StringContainsProcessor());
        
        // Build and run the transformation
        launcher.buildModel();
        launcher.process();
        
        // Generate the fixed files
        launcher.prettyprint();
    }
}

/**
 * Processor that fixes StringContains constructor calls.
 */
class StringContainsProcessor extends AbstractProcessor<CtConstructorCall> {
    
    @Override
    public boolean isToBeProcessed(CtConstructorCall candidate) {
        // Check if this is a StringContains or StringStartsWith constructor call
        if (candidate.getExecutable() != null && 
            candidate.getExecutable().getDeclaringType() != null) {
            String typeName = candidate.getExecutable().getDeclaringType().getQualifiedName();
            return typeName.contains("StringContains") || typeName.contains("StringStartsWith");
        }
        return false;
    }

    @Override
    public void process(CtConstructorCall constructorCall) {
        // Verify it's calling StringContains or StringStartsWith constructor with 2 arguments
        // where first is boolean and second is String
        if (constructorCall.getArguments().size() == 2) {
            try {
                // Cast the arguments - this is a bit tricky in Spoon
                Object firstArgObj = constructorCall.getArguments().get(0);
                Object secondArgObj = constructorCall.getArguments().get(1);
                
                if (firstArgObj instanceof CtLiteral && 
                    secondArgObj instanceof CtLiteral) {
                    CtLiteral<?> firstLiteral = (CtLiteral<?>) firstArgObj;
                    CtLiteral<?> secondLiteral = (CtLiteral<?>) secondArgObj;
                    
                    // Check if first argument is a boolean literal and second is a string
                    if (firstLiteral.getValue() instanceof Boolean && 
                        secondLiteral.getValue() instanceof String) {
                        
                        // Replace the constructor call with one that only takes the string argument
                        List<CtExpression<?>> newArgs = new ArrayList<>();
                        newArgs.add(secondLiteral);
                        constructorCall.setArguments(newArgs);
                    }
                }
            } catch (Exception e) {
                // Silently ignore errors to prevent breaking the transformation
                // This can happen with complex AST structures
            }
        }
    }
}