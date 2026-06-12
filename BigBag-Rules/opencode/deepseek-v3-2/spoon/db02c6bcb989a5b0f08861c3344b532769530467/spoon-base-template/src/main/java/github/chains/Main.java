package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher spoon = new Launcher();
        spoon.addInputResource(sourceDir);
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setAutoImports(true);
        spoon.getEnvironment().setCommentEnabled(true);
        spoon.getEnvironment().setIgnoreDuplicateDeclarations(true);
        spoon.getEnvironment().setShouldCompile(false);
        
        // Add our processor for fixing Hamcrest API changes
        spoon.addProcessor(new HamcrestConstructorFixProcessor());
        
        // Run the transformation
        spoon.run();
        
        System.out.println("Transformation completed successfully!");
    }
}

class HamcrestConstructorFixProcessor extends AbstractProcessor<CtConstructorCall<?>> {
    
    @Override
    public void init() {
        super.init();
    }
    
    @Override
    public void process(CtConstructorCall<?> constructorCall) {
        // Get the constructor's type reference
        CtTypeReference<?> constructedType = constructorCall.getType();
        if (constructedType == null) {
            return;
        }
        
        String typeName = constructedType.getQualifiedName();
        
        // Check if this is a StringContains or StringStartsWith constructor call
        if (!typeName.equals("org.hamcrest.core.StringContains") && 
            !typeName.equals("org.hamcrest.core.StringStartsWith")) {
            return;
        }
        
        List<CtExpression<?>> arguments = constructorCall.getArguments();
        
        // The old API was: StringContains(boolean ignoreCase, String substring)
        // The new API is:  StringContains(String substring)
        // We need to remove the first boolean parameter
        
        if (arguments.size() >= 2) {
            CtExpression<?> firstArg = arguments.get(0);
            
            // Check if the first argument is a boolean (literal or expression)
            if (isBooleanExpression(firstArg)) {
                // Create a new arguments list without the first boolean argument
                List<CtExpression<?>> newArgs = new ArrayList<>(arguments.subList(1, arguments.size()));
                constructorCall.setArguments(newArgs);
                
                System.out.println("Fixed " + typeName + " constructor at " + 
                    constructorCall.getPosition());
            }
        }
    }
    
    private boolean isBooleanExpression(CtExpression<?> expression) {
        // Check if expression is a boolean literal
        if (expression instanceof CtLiteral) {
            CtLiteral<?> literal = (CtLiteral<?>) expression;
            Object value = literal.getValue();
            return value instanceof Boolean;
        }
        
        // Check if expression type is boolean
        CtTypeReference<?> type = expression.getType();
        if (type != null) {
            String typeName = type.getQualifiedName();
            return typeName.equals("boolean") || 
                   typeName.equals("java.lang.Boolean") ||
                   // Also handle some common boolean variable/field names
                   typeName.equals("boolean") || typeName.equals("Boolean");
        }
        
        // If we can't determine, assume it's boolean based on common patterns
        // This is conservative - we only remove if we're confident
        String exprString = expression.toString();
        return exprString.equals("true") || exprString.equals("false") ||
               exprString.equals("TRUE") || exprString.equals("FALSE");
    }
    
    @Override
    public Set<Class<? extends spoon.reflect.declaration.CtElement>> getProcessedElementTypes() {
        return Collections.singleton(CtConstructorCall.class);
    }
}