package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import java.util.Collections;
import java.util.List;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FixHamcrest {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        // Include main sources for type resolution
        launcher.addInputResource("/workspace/docker-adapter/src/main/java");
        
        // Add test files excluding package-info.java to avoid conflicts
        try (Stream<Path> paths = Files.walk(Paths.get("/workspace/docker-adapter/src/test/java"))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                 .forEach(p -> launcher.addInputResource(p.toString()));
        }
        
        // Output to a separate directory to avoid overwriting and mixing
        launcher.setSourceOutputDirectory("spooned");
        
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new HamcrestProcessor());
        launcher.run();
    }
    
    public static class HamcrestProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            CtTypeReference<?> type = candidate.getType();
            if (type == null) return false;
            String name = type.getQualifiedName();
            return name.equals("org.hamcrest.core.StringContains") || 
                   name.equals("org.hamcrest.core.StringStartsWith");
        }

        @Override
        public void process(CtConstructorCall<?> ctConstructorCall) {
            List<CtExpression<?>> arguments = ctConstructorCall.getArguments();
            if (arguments.size() == 2) {
                CtExpression<?> firstArg = arguments.get(0);
                if (firstArg instanceof CtLiteral) {
                    Object val = ((CtLiteral<?>) firstArg).getValue();
                    if (val instanceof Boolean) {
                        boolean ignoreCase = (Boolean) val;
                        CtExpression<?> stringArg = arguments.get(1);
                        
                        if (!ignoreCase) {
                            // false -> just remove the boolean arg
                            // new StringContains(false, "s") -> new StringContains("s")
                            ctConstructorCall.removeArgument(firstArg);
                        } else {
                            // true -> use Matchers.containsStringIgnoringCase("s")
                            Factory factory = getFactory();
                            
                            // Create reference to Matchers class
                            CtTypeReference<?> matchersRef = factory.createReference("org.hamcrest.Matchers");
                            
                            String methodName = "";
                            String simpleName = ctConstructorCall.getType().getSimpleName();
                            if (simpleName.equals("StringContains")) {
                                methodName = "containsStringIgnoringCase";
                            } else if (simpleName.equals("StringStartsWith")) {
                                methodName = "startsWithIgnoringCase";
                            }
                            
                            if (!methodName.isEmpty()) {
                                spoon.reflect.reference.CtExecutableReference<Object> ref = factory.Core().createExecutableReference();
                                ref.setDeclaringType(matchersRef);
                                ref.setSimpleName(methodName);
                                // The return type of the method is Matcher<String>, but we can use the constructor type as approximation or just Object
                                ref.setType((CtTypeReference) ctConstructorCall.getType()); 
                                ref.setParameters(Collections.singletonList(factory.createReference("java.lang.String")));
                                ref.setStatic(true);

                                CtInvocation invocation = factory.createInvocation(
                                    factory.createTypeAccess(matchersRef),
                                    ref,
                                    stringArg
                                );
                                ctConstructorCall.replace(invocation);
                            }
                        }
                    }
                }
            }
        }
    }
}
