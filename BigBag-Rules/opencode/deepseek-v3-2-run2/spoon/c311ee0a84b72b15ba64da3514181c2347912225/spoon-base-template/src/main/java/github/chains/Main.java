package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking API changes in Hamcrest library.
 * 
 * Breaking Change Pattern:
 * - Old API: StringContains(boolean ignoreCase, String substring)
 * - New API: StringContains(String substring) [case-sensitive only]
 * 
 * Transformation:
 * - Removes the boolean ignoreCase parameter from StringContains constructor calls
 * - Removes the boolean ignoreCase parameter from StringStartsWith constructor calls
 * 
 * Limitations:
 * - Case sensitivity information is lost (ignoreCase = true becomes case-sensitive)
 * - For better transformation that preserves case sensitivity, use static methods:
 *   - StringContains(true, text) -> Matchers.containsStringIgnoringCase(text)
 *   - StringContains(false, text) -> Matchers.containsString(text)
 * 
 * This transformation is generic and can be applied to any Java project
 * facing this breaking change by providing the source directory as argument.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        transformStringContainsCalls(model);
        transformStringStartsWithCalls(model);
        
        launcher.prettyprint();
        
        System.out.println("Transformation completed.");
    }
    
    private static void transformStringContainsCalls(CtModel model) {
        List<CtConstructorCall<?>> containsCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef == null) return false;
                
                String qualifiedName = typeRef.getQualifiedName();
                if (!"org.hamcrest.core.StringContains".equals(qualifiedName)) {
                    return false;
                }
                
                List<?> arguments = constructorCall.getArguments();
                return arguments.size() == 2;
            }
        });
        
        System.out.println("Found " + containsCalls.size() + " StringContains(boolean, String) calls to transform");
        
        for (CtConstructorCall<?> constructorCall : containsCalls) {
            try {
                List<?> arguments = constructorCall.getArguments();
                
                boolean ignoreCase = false;
                Object firstArg = arguments.get(0);
                if (firstArg instanceof CtLiteral) {
                    CtLiteral<?> booleanLiteral = (CtLiteral<?>) firstArg;
                    ignoreCase = (Boolean) booleanLiteral.getValue();
                }
                
                constructorCall.removeArgument((CtExpression<?>) firstArg);
                
                System.out.println("Transformed: StringContains(" + ignoreCase + ", ...) -> StringContains(...)");
                if (ignoreCase) {
                    System.err.println("  WARNING: Case sensitivity lost (was ignoreCase=true)");
                }
            } catch (Exception e) {
                System.err.println("Error transforming StringContains call: " + e.getMessage());
            }
        }
    }
    
    private static void transformStringStartsWithCalls(CtModel model) {
        List<CtConstructorCall<?>> startsWithCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> constructorCall) {
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef == null) return false;
                
                String qualifiedName = typeRef.getQualifiedName();
                if (!"org.hamcrest.core.StringStartsWith".equals(qualifiedName)) {
                    return false;
                }
                
                List<?> arguments = constructorCall.getArguments();
                return arguments.size() == 2;
            }
        });
        
        System.out.println("Found " + startsWithCalls.size() + " StringStartsWith(boolean, String) calls to transform");
        
        for (CtConstructorCall<?> constructorCall : startsWithCalls) {
            try {
                List<?> arguments = constructorCall.getArguments();
                
                boolean ignoreCase = false;
                Object firstArg = arguments.get(0);
                if (firstArg instanceof CtLiteral) {
                    CtLiteral<?> booleanLiteral = (CtLiteral<?>) firstArg;
                    ignoreCase = (Boolean) booleanLiteral.getValue();
                }
                
                constructorCall.removeArgument((CtExpression<?>) firstArg);
                
                System.out.println("Transformed: StringStartsWith(" + ignoreCase + ", ...) -> StringStartsWith(...)");
                if (ignoreCase) {
                    System.err.println("  WARNING: Case sensitivity lost (was ignoreCase=true)");
                }
            } catch (Exception e) {
                System.err.println("Error transforming StringStartsWith call: " + e.getMessage());
            }
        }
    }
}