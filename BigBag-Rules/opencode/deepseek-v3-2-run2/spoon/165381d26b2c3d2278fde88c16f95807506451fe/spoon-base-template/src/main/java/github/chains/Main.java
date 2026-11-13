package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = Query.getElements(model.getRootPackage(), new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                String methodName = invocation.getExecutable().getSimpleName();
                if (!"parse".equals(methodName)) {
                    return false;
                }
                
                // Check if this is a JavaParser.parse() call
                if (invocation.getTarget() != null) {
                    if (invocation.getTarget() instanceof CtConstructorCall) {
                        CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) invocation.getTarget();
                        CtTypeReference<?> typeRef = constructorCall.getType();
                        if (typeRef != null && "com.github.javaparser.JavaParser".equals(typeRef.getQualifiedName())) {
                            return true;
                        }
                    } else if (invocation.getTarget() instanceof CtTypeAccess) {
                        CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) invocation.getTarget();
                        CtTypeReference<?> typeRef = typeAccess.getAccessedType();
                        if (typeRef != null && "com.github.javaparser.JavaParser".equals(typeRef.getQualifiedName())) {
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        
        // Transform each invocation
        for (CtInvocation<?> invocation : invocations) {
            // Create StaticJavaParser type access
            CtTypeReference<?> staticJavaParserType = launcher.getFactory().Type().createReference("com.github.javaparser.StaticJavaParser");
            CtTypeAccess<?> staticJavaParserAccess = launcher.getFactory().Code().createTypeAccess(staticJavaParserType);
            
            // Create new invocation
            CtInvocation<?> newInvocation = launcher.getFactory().Code().createInvocation(
                staticJavaParserAccess,
                invocation.getExecutable().clone(),
                invocation.getArguments()
            );
            
            // Replace the invocation
            invocation.replace(newInvocation);
        }
        
        // Write transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Processed " + invocations.size() + " JavaParser.parse() calls.");
    }
}