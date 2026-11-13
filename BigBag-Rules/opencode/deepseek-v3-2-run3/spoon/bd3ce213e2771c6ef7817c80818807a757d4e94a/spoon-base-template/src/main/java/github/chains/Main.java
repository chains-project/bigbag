package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        // Find all writeValue invocations
        model.getElements(new TypeFilter<>(CtInvocation.class)).forEach(invocation -> {
            if (isWriteValueInvocation(invocation)) {
                wrapWithTryCatch(invocation);
            }
        });
        
        // Output transformed code
        launcher.prettyprint();
    }
    
    private static boolean isWriteValueInvocation(CtInvocation<?> invocation) {
        String methodName = invocation.getExecutable().getSimpleName();
        return "writeValue".equals(methodName);
    }
    
    private static void wrapWithTryCatch(CtInvocation<?> invocation) {
        Factory factory = invocation.getFactory();
        
        // Create a try block
        CtTry tryBlock = factory.createTry();
        
        // Create try body with the original invocation
        CtBlock<?> tryBody = factory.createBlock();
        tryBody.addStatement(invocation.clone());
        tryBlock.setBody(tryBody);
        
        // Create catch block for Exception
        CtCatch catchBlock = factory.createCtCatch("e", Exception.class, factory.createBlock());
        
        // Create catch body: throw new IOException(e)
        CtBlock<?> catchBody = factory.createBlock();
        
        // Create IOException constructor call
        CtConstructorCall<IOException> ioExceptionConstructor = factory.createConstructorCall(
            factory.createCtTypeReference(IOException.class)
        );
        ioExceptionConstructor.addArgument(factory.createVariableRead(factory.createVariableReference("e", factory.createCtTypeReference(Exception.class)), false));
        
        // Create throw statement
        CtThrow throwStmt = factory.createThrow();
        throwStmt.setThrownExpression(ioExceptionConstructor);
        
        catchBody.addStatement(throwStmt);
        catchBlock.setBody(catchBody);
        
        tryBlock.addCatcher(catchBlock);
        
        // Replace the invocation with the try block
        invocation.replace(tryBlock);
    }
}