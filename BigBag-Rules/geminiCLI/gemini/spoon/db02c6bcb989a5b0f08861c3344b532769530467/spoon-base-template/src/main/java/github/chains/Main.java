package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Set input directory
        launcher.addInputResource("/workspace/docker-adapter/src/test/java");
        
        // Output directory
        launcher.setSourceOutputDirectory("/workspace/spoon_output");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);

        launcher.buildModel();
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();

        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        
        for (CtConstructorCall<?> call : constructorCalls) {
            CtTypeReference<?> typeRef = call.getType();
            if (typeRef == null) continue;
            
            String qualifiedName = typeRef.getQualifiedName();
            String simpleName = typeRef.getSimpleName();
            
            boolean isStringContains = "org.hamcrest.core.StringContains".equals(qualifiedName) || "StringContains".equals(simpleName);
            boolean isStringStartsWith = "org.hamcrest.core.StringStartsWith".equals(qualifiedName) || "StringStartsWith".equals(simpleName);
            
            if (isStringContains || isStringStartsWith) {
                if (call.getArguments().size() == 2) {
                    CtExpression<?> firstArg = call.getArguments().get(0);
                    CtExpression<?> secondArg = call.getArguments().get(1);
                    
                    if (firstArg instanceof CtLiteral) {
                        Object val = ((CtLiteral<?>) firstArg).getValue();
                        if (val instanceof Boolean) {
                            boolean ignoreCase = (Boolean) val;
                            
                            if (ignoreCase) {
                                // Replace with Matchers.containsStringIgnoringCase / startsWithIgnoringCase
                                String methodName = isStringContains ? "containsStringIgnoringCase" : "startsWithIgnoringCase";
                                
                                CtTypeReference<?> matchersType = factory.Type().createReference("org.hamcrest.Matchers");
                                CtExecutableReference<?> ref = factory.Executable().createReference(
                                    matchersType,
                                    factory.Type().createReference("org.hamcrest.Matcher"),
                                    methodName,
                                    factory.Type().stringType()
                                );
                                
                                CtInvocation invocation = factory.Code().createInvocation(
                                    factory.Code().createTypeAccess(matchersType),
                                    ref,
                                    secondArg
                                );
                                
                                call.replace(invocation);
                            } else {
                                // Replace with new StringContains(secondArg) (remove first arg)
                                call.removeArgument(firstArg);
                            }
                        }
                    }
                }
            }
        }
        
        launcher.prettyprint();
        System.out.println("Transformation complete.");
    }
}
