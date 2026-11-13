package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

/**
 * Generic Spoon transformation to fix Hamcrest API breaking changes.
 * 
 * Transformation rules:
 * 1. new StringContains("string") -> Matchers.containsString("string")
 * 2. new StringContains(false, "string") -> Matchers.containsString("string")  
 * 3. new StringContains(true, "string") -> Matchers.containsStringIgnoringCase("string")
 * 4. new StringStartsWith("prefix") -> Matchers.startsWith("prefix")
 * 5. new StringStartsWith(false, "prefix") -> Matchers.startsWith("prefix")
 * 6. new StringStartsWith(true, "prefix") -> Matchers.startsWithIgnoringCase("prefix")
 * 
 * This transformation is generic and can be applied to any Java project
 * affected by the Hamcrest API breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformer.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add processor for Hamcrest API transformations
        launcher.addProcessor(new HamcrestApiTransformer());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static class HamcrestApiTransformer extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            try {
                String typeName = constructorCall.getType().getQualifiedName();
                
                if ("org.hamcrest.core.StringContains".equals(typeName)) {
                    transformStringContains(constructorCall);
                } else if ("org.hamcrest.core.StringStartsWith".equals(typeName)) {
                    transformStringStartsWith(constructorCall);
                }
            } catch (Exception e) {
                System.err.println("Error processing constructor call at " + 
                    constructorCall.getPosition() + ": " + e.getMessage());
            }
        }
        
        private void transformStringContains(CtConstructorCall<?> constructorCall) {
            List<CtExpression<?>> args = constructorCall.getArguments();
            
            if (args.size() == 1) {
                // new StringContains("string") -> Matchers.containsString("string")
                replaceWithMatchersMethod(constructorCall, "containsString", args);
            } else if (args.size() == 2) {
                // Check if first argument is boolean literal
                CtExpression<?> firstArg = args.get(0);
                if (firstArg instanceof CtLiteral<?> literal) {
                    Object value = literal.getValue();
                    if (value instanceof Boolean) {
                        boolean ignoreCase = (Boolean) value;
                        String methodName = ignoreCase ? "containsStringIgnoringCase" : "containsString";
                        // Use only the second argument (string)
                        List<CtExpression<?>> stringArg = List.of(args.get(1));
                        replaceWithMatchersMethod(constructorCall, methodName, stringArg);
                    }
                }
            }
        }
        
        private void transformStringStartsWith(CtConstructorCall<?> constructorCall) {
            List<CtExpression<?>> args = constructorCall.getArguments();
            
            if (args.size() == 1) {
                // new StringStartsWith("prefix") -> Matchers.startsWith("prefix")
                replaceWithMatchersMethod(constructorCall, "startsWith", args);
            } else if (args.size() == 2) {
                // Check if first argument is boolean literal
                CtExpression<?> firstArg = args.get(0);
                if (firstArg instanceof CtLiteral<?> literal) {
                    Object value = literal.getValue();
                    if (value instanceof Boolean) {
                        boolean ignoreCase = (Boolean) value;
                        String methodName = ignoreCase ? "startsWithIgnoringCase" : "startsWith";
                        // Use only the second argument (string)
                        List<CtExpression<?>> stringArg = List.of(args.get(1));
                        replaceWithMatchersMethod(constructorCall, methodName, stringArg);
                    }
                }
            }
        }
        
        private void replaceWithMatchersMethod(CtConstructorCall<?> constructorCall, 
                                               String methodName, 
                                               List<CtExpression<?>> arguments) {
            Factory factory = getFactory();
            
            // Create Matchers type reference
            CtTypeReference<?> matchersType = factory.Type().createReference("org.hamcrest.Matchers");
            
            // Create executable reference for the static method
            CtExecutableReference<?> methodRef = factory.Executable().createReference(
                matchersType,
                null,
                methodName,
                factory.Type().createReference(String.class)
            );
            
            // Create type access for Matchers class
            CtExpression<?> target = factory.createTypeAccess(matchersType);
            
            // Create the method invocation
            CtInvocation<?> methodCall = factory.Code().createInvocation(
                target,
                methodRef,
                arguments.toArray(new CtExpression<?>[0])
            );
            
            // Replace the constructor call with the method invocation
            constructorCall.replace(methodCall);
            
            System.out.println("Transformed " + constructorCall.getType().getSimpleName() + 
                " at " + constructorCall.getPosition() + 
                " to Matchers." + methodName + "()");
        }
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            String typeName = candidate.getType().getQualifiedName();
            return "org.hamcrest.core.StringContains".equals(typeName) ||
                   "org.hamcrest.core.StringStartsWith".equals(typeName);
        }
    }
}