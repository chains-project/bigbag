package github.chains;

import spoon.Launcher;
import spoon.SpoonAPI;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create Spoon launcher
        SpoonAPI spoon = new Launcher();
        spoon.addInputResource(sourceDir);
        spoon.getEnvironment().setNoClasspath(true);
        spoon.getEnvironment().setAutoImports(true);
        
        // Build model
        CtModel model = spoon.buildModel();
        
        // Find all method invocations
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                // Check if this is a call to ObjectMapper.readValue
                CtTypeReference<?> targetType = invocation.getExecutable().getDeclaringType();
                if (targetType != null && targetType.getQualifiedName().equals("com.fasterxml.jackson.databind.ObjectMapper")) {
                    String methodName = invocation.getExecutable().getSimpleName();
                    return methodName.equals("readValue");
                }
                return false;
            }
        });
        
        System.out.println("Found " + invocations.size() + " calls to ObjectMapper.readValue");
        
        // For each invocation, check the enclosing method's throws clause
        for (CtInvocation<?> invocation : invocations) {
            CtMethod<?> enclosingMethod = invocation.getParent(CtMethod.class);
            if (enclosingMethod != null) {
                System.out.println("Method " + enclosingMethod.getSignature() + " calls readValue at line " + invocation.getPosition().getLine());
                
                // Check if method declares IOException (which covers StreamReadException)
                boolean hasIOException = false;
                for (CtTypeReference<?> exceptionType : enclosingMethod.getThrownTypes()) {
                    if (exceptionType.getQualifiedName().equals("java.io.IOException")) {
                        hasIOException = true;
                        break;
                    }
                }
                
                if (!hasIOException) {
                    System.out.println("  -> Method does not declare IOException. May need to update throws clause.");
                }
            }
        }
        
        // Also look for JsonParseException catches that should be StreamReadException
        System.out.println("\nNote: In Jackson 2.13+, JsonParseException was replaced by StreamReadException.");
        System.out.println("Consider updating catch blocks and imports if JsonParseException is used.");
    }
}