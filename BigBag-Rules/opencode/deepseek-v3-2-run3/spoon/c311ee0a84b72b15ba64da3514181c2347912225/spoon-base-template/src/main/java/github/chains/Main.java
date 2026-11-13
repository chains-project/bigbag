package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic transformation rule for Hamcrest StringContains/StringStartsWith breaking change.
 * 
 * Breaking Change: Constructor signature changed from:
 *   Old: StringContains(String substring)
 *        StringStartsWith(String prefix)
 *   New: StringContains(boolean ignoreCase, String substring)
 *        StringStartsWith(boolean ignoreCase, String prefix)
 * 
 * Transformation: Adds 'false' as first parameter to constructor calls with single string argument.
 * This transformation is generic and can be applied to any project affected by this breaking change.
 * Simply change the input source directory path.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Hamcrest StringContains/StringStartsWith constructors");
            System.err.println("from old signature (String) to new signature (boolean, String)");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setShouldCompile(false);
        
        try {
            launcher.buildModel();
            
            // Find all constructor calls for StringContains and StringStartsWith
            List<CtConstructorCall> constructorCalls = launcher.getModel()
                .getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                    @Override
                    public boolean matches(CtConstructorCall element) {
                        if (element.getType() == null || element.getType().getQualifiedName() == null) {
                            return false;
                        }
                        
                        String typeName = element.getType().getQualifiedName();
                        return typeName.equals("org.hamcrest.core.StringContains") ||
                               typeName.equals("org.hamcrest.core.StringStartsWith");
                    }
                });
            
            System.out.println("Found " + constructorCalls.size() + " constructor calls to transform");
            
            int transformed = 0;
            for (CtConstructorCall call : constructorCalls) {
                // Check if already has the boolean parameter
                if (call.getArguments().size() == 1) {
                    // Single argument constructor - needs transformation
                    CtExpression<?> originalArg = (CtExpression<?>) call.getArguments().get(0);
                    
                    // Create new constructor call with 'false' as first parameter
                    CtConstructorCall newCall = launcher.getFactory().createConstructorCall(
                        launcher.getFactory().Type().createReference(call.getType().getQualifiedName())
                    );
                    
                    // Add false as first argument (case-sensitive matching)
                    CtLiteral<Boolean> falseLiteral = launcher.getFactory().createLiteral(false);
                    newCall.addArgument(falseLiteral);
                    
                    // Add the original string argument as second argument
                    newCall.addArgument(originalArg);
                    
                    // Replace the old call with the new one
                    call.replace(newCall);
                    transformed++;
                    System.out.println("Transformed constructor call at position: " + call.getPosition());
                } else if (call.getArguments().size() == 2) {
                    // Already has two arguments - check if first is boolean
                    CtExpression<?> firstArg = (CtExpression<?>) call.getArguments().get(0);
                    if (firstArg instanceof CtLiteral) {
                        Object value = ((CtLiteral<?>) firstArg).getValue();
                        if (value.equals(false) || value.equals(true)) {
                            // Already has boolean parameter - correct for new API
                        } else {
                            System.out.println("Warning: Constructor has 2 args, but first is not boolean literal at: " + call.getPosition());
                        }
                    } else {
                        // First argument is not a literal - could be variable or expression
                        System.out.println("Info: Constructor already has 2 args at: " + call.getPosition());
                    }
                }
            }
            
            System.out.println("Successfully transformed " + transformed + " constructor calls");
            
            // Output directory for transformed code (can be same as input)
            String outputDir = args.length > 1 ? args[1] : sourceDir;
            launcher.setSourceOutputDirectory(outputDir);
            launcher.prettyprint();
            
            System.out.println("Transformed code written to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}