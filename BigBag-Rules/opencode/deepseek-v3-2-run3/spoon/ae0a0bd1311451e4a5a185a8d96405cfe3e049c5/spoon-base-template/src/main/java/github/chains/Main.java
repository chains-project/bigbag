package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Transform StringContains constructor calls
        transformHamcrestConstructorCalls(
            model, 
            factory, 
            "org.hamcrest.core.StringContains",
            "containsString"
        );
        
        // Transform StringStartsWith constructor calls  
        transformHamcrestConstructorCalls(
            model,
            factory,
            "org.hamcrest.core.StringStartsWith",
            "startsWith"
        );
        
        // Output transformed code in place
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void transformHamcrestConstructorCalls(
        CtModel model, 
        Factory factory,
        String className,
        String staticMethodName
    ) {
        // Find all constructor calls for the given class
        List<CtConstructorCall> constructorCalls = model
            .getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall constructorCall) {
                    CtTypeReference typeRef = constructorCall.getType();
                    if (typeRef == null) {
                        return false;
                    }
                    String qualifedName = typeRef.getQualifiedName();
                    return qualifedName != null && qualifedName.equals(className);
                }
            });
        
        System.out.println("Found " + constructorCalls.size() + " constructor calls for " + className);
        
        for (CtConstructorCall constructorCall : constructorCalls) {
            List<CtExpression> arguments = constructorCall.getArguments();
            
            // Always use the static method, ignoring boolean parameter if present
            // The static method is case-sensitive only
            CtExpression stringArg;
            if (arguments.size() == 2) {
                // Two arguments: boolean and string, ignore the boolean
                stringArg = arguments.get(1);
                System.out.println("  Ignoring boolean parameter, using string: " + stringArg.toString());
            } else if (arguments.size() == 1) {
                // Single argument: string
                stringArg = arguments.get(0);
            } else {
                System.out.println("  Unexpected number of arguments: " + arguments.size() + ", skipping");
                continue;
            }
            
            // Create static method invocation from the class itself
            CtTypeReference classRef = factory.Type().createReference(className);
            
            // Create method reference
            CtExecutableReference methodRef = factory.createExecutableReference();
            methodRef.setDeclaringType(classRef);
            methodRef.setSimpleName(staticMethodName);
            methodRef.setType(factory.Type().createReference("org.hamcrest.Matcher"));
            
            // Create invocation
            CtInvocation invocation = factory.createInvocation();
            invocation.setExecutable(methodRef);
            invocation.addArgument(stringArg.clone());
            
            // Replace constructor call with static method invocation
            constructorCall.replace(invocation);
            
            System.out.println("Transformed " + className + " constructor call to " + 
                staticMethodName + "()");
        }
    }
}