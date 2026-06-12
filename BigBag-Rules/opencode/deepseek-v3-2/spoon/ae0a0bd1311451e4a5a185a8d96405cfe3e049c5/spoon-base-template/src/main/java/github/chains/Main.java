package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.reflect.code.CtConstructorCallImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import java.util.List;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Transform StringContains constructor calls
        List<CtConstructorCall> stringContainsCalls = model.getElements(
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    CtTypeReference typeRef = element.getType();
                    if (typeRef == null) return false;
                    
                    String typeName = typeRef.getQualifiedName();
                    return "org.hamcrest.core.StringContains".equals(typeName) ||
                           "org.hamcrest.core.StringStartsWith".equals(typeName);
                }
            }
        );
        
        for (CtConstructorCall constrCall : stringContainsCalls) {
            transformConstructorCall(constrCall);
        }
        
        // Write transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Processed " + stringContainsCalls.size() + " constructor calls.");
    }
    
    private static void transformConstructorCall(CtConstructorCall constrCall) {
        List<CtExpression> args = constrCall.getArguments();
        if (args.size() != 2) {
            System.out.println("Warning: Expected 2 arguments for " + 
                constrCall.getType().getQualifiedName() + " but found " + args.size());
            return;
        }
        
        CtExpression firstArg = args.get(0);
        CtExpression secondArg = args.get(1);
        
        // Check if first argument is a boolean literal
        if (!(firstArg instanceof CtLiteral)) {
            System.out.println("Warning: First argument is not a literal boolean, skipping: " + constrCall);
            return;
        }
        
        CtLiteral booleanLiteral = (CtLiteral) firstArg;
        if (!(booleanLiteral.getValue() instanceof Boolean)) {
            System.out.println("Warning: First argument is not a boolean, skipping: " + constrCall);
            return;
        }
        
        boolean isCaseInsensitive = (Boolean) booleanLiteral.getValue();
        String textArg = secondArg.toString();
        
        String typeName = constrCall.getType().getQualifiedName();
        String methodName;
        
        if ("org.hamcrest.core.StringContains".equals(typeName)) {
            methodName = isCaseInsensitive ? "containsStringIgnoringCase" : "containsString";
        } else if ("org.hamcrest.core.StringStartsWith".equals(typeName)) {
            methodName = isCaseInsensitive ? "startsWithIgnoringCase" : "startsWith";
        } else {
            return;
        }
        
        // Create static method invocation with fully qualified name
        // Use fully qualified name to avoid import issues
        CtTypeReference typeRef = constrCall.getFactory().Type().createReference(typeName);
        CtExecutableReference methodRef = constrCall.getFactory().Executable().createReference(
            typeRef,
            typeRef,
            methodName,
            constrCall.getFactory().Type().createReference(String.class)
        );
        
        CtInvocation staticInvocation = constrCall.getFactory().createInvocation(
            constrCall.getFactory().createTypeAccess(typeRef),
            methodRef,
            secondArg
        );
        
        // Replace constructor call with static method invocation
        constrCall.replace(staticInvocation);
        
        System.out.println("Transformed: " + typeName + "(" + isCaseInsensitive + ", " + textArg + 
                          ") -> " + typeName + "." + methodName + "(" + textArg + ")");
    }
}