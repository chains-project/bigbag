package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.declaration.CtElement;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setLevel("OFF");
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Transform Hamcrest constructor calls to static factory methods
        transformHamcrestConstructorCalls(model.getRootPackage(), factory, 
            "org.hamcrest.core.StringContains", "containsString", "containsStringIgnoringCase");
        
        transformHamcrestConstructorCalls(model.getRootPackage(), factory,
            "org.hamcrest.core.StringStartsWith", "startsWith", "startsWithIgnoringCase");
        
        // Output transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }
    
    private static void transformHamcrestConstructorCalls(CtElement root, Factory factory,
            String className, String caseSensitiveMethod, String ignoreCaseMethod) {
        List<CtConstructorCall> constructorCalls = Query.getElements(root, 
            new Filter<CtConstructorCall>() {
                @Override
                public boolean matches(CtConstructorCall constructorCall) {
                    CtTypeReference typeRef = constructorCall.getType();
                    if (typeRef == null) return false;
                    String qualifiedName = typeRef.getQualifiedName();
                    return className.equals(qualifiedName) &&
                           constructorCall.getArguments().size() == 2;
                }
            });
        
        for (CtConstructorCall call : constructorCalls) {
            List<CtExpression> args = call.getArguments();
            if (args.size() == 2) {
                CtExpression firstArg = args.get(0);
                CtExpression secondArg = args.get(1);
                
                if (firstArg instanceof CtLiteral) {
                    CtLiteral literal = (CtLiteral) firstArg;
                    Object value = literal.getValue();
                    
                    if (value instanceof Boolean) {
                        boolean ignoreCase = (Boolean) value;
                        String methodName = ignoreCase ? ignoreCaseMethod : caseSensitiveMethod;
                        
                        // Create static method call: ClassName.methodName(string)
                        CtTypeReference typeRef = factory.Type().createReference(className);
                        CtTypeReference stringType = factory.Type().createReference(String.class);
                        
                        CtExecutableReference methodRef = factory.Method().createReference(
                            typeRef,
                            factory.Type().createReference(java.lang.Object.class),
                            methodName,
                            stringType);
                        
                        CtInvocation staticCall = factory.Code().createInvocation(
                            factory.Code().createTypeAccess(typeRef.clone()),
                            methodRef,
                            secondArg);
                        
                        // Replace constructor call with static method call
                        call.replace(staticCall);
                    }
                }
            }
        }
    }
}