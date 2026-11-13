package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        // Use a temporary output directory to avoid conflicts
        java.io.File outputDir = new java.io.File("/tmp/spoon-output");
        outputDir.mkdirs();
        launcher.setSourceOutputDirectory(outputDir);
        
        // Create and add the transformation
        launcher.addProcessor(new MinaCoreApiTransformation());
        
        // Run the transformation
        launcher.run();
        
        // Write the transformed code back to files
        launcher.prettyprint();
    }
    
    public static class MinaCoreApiTransformation extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public void process(CtInvocation<?> invocation) {
            // Check if this is a call to IoBuffer.allocate(int)
            if (isIoBufferStaticMethodCall(invocation, "allocate") && 
                invocation.getArguments().size() == 1 &&
                isIntArgument(invocation.getArguments().get(0))) {
                
                System.out.println("Found IoBuffer.allocate call at: " + invocation.getPosition());
                transformIoBufferAllocate(invocation);
            }
            
            // Check if this is a call to IoBuffer.wrap(byte[])
            else if (isIoBufferStaticMethodCall(invocation, "wrap") &&
                     invocation.getArguments().size() == 1 &&
                     isByteArrayArgument(invocation.getArguments().get(0))) {
                
                System.out.println("Found IoBuffer.wrap call at: " + invocation.getPosition());
                transformIoBufferWrap(invocation);
            }
        }
        
        private boolean isIoBufferStaticMethodCall(CtInvocation<?> invocation, String methodName) {
            return invocation.getExecutable() != null &&
                   invocation.getExecutable().getSimpleName().equals(methodName) &&
                   invocation.getTarget() instanceof CtTypeAccess &&
                   ((CtTypeAccess<?>) invocation.getTarget()).getAccessedType() != null &&
                   ((CtTypeAccess<?>) invocation.getTarget()).getAccessedType().getQualifiedName().equals("org.apache.mina.core.buffer.IoBuffer");
        }
        
        private boolean isIntArgument(Object argument) {
            // Check if argument is an integer literal or expression that evaluates to int
            return argument instanceof spoon.reflect.code.CtLiteral ||
                   (argument instanceof spoon.reflect.code.CtExpression && 
                    ((spoon.reflect.code.CtExpression<?>) argument).getType() != null &&
                    ((spoon.reflect.code.CtExpression<?>) argument).getType().getQualifiedName().equals("int"));
        }
        
        private boolean isByteArrayArgument(Object argument) {
            if (argument instanceof spoon.reflect.code.CtExpression) {
                spoon.reflect.code.CtExpression<?> expr = (spoon.reflect.code.CtExpression<?>) argument;
                if (expr.getType() != null) {
                    String typeName = expr.getType().getQualifiedName();
                    return typeName.equals("byte[]") || 
                           typeName.equals("java.nio.ByteBuffer") ||
                           typeName.equals("java.nio.MappedByteBuffer");
                }
            }
            return false;
        }
        
        private void transformIoBufferAllocate(CtInvocation<?> invocation) {
            System.out.println("Transforming IoBuffer.allocate call");
            Factory factory = invocation.getFactory();
            
            // Create: IoBuffer.getAllocator().allocate(capacity, false)
            CtTypeAccess<?> ioBufferTypeAccess = factory.createTypeAccess(
                factory.Type().createReference("org.apache.mina.core.buffer.IoBuffer")
            );
            
            CtInvocation<?> getAllocatorCall = factory.createInvocation(
                ioBufferTypeAccess,
                factory.createExecutableReference()
                    .setDeclaringType(factory.Type().createReference("org.apache.mina.core.buffer.IoBuffer"))
                    .setSimpleName("getAllocator")
            );
            
            List<spoon.reflect.code.CtExpression<?>> arguments = invocation.getArguments();
            CtInvocation<?> newAllocateCall = factory.createInvocation(
                getAllocatorCall,
                factory.createExecutableReference()
                    .setDeclaringType(factory.Type().createReference("org.apache.mina.core.buffer.IoBufferAllocator"))
                    .setSimpleName("allocate")
            );
            
            // Add capacity argument
            newAllocateCall.addArgument(arguments.get(0));
            
            // Add direct=false argument
            newAllocateCall.addArgument(factory.createLiteral(false));
            
            // Replace the original invocation
            System.out.println("Replacing invocation with: " + newAllocateCall);
            invocation.replace(newAllocateCall);
            System.out.println("Replacement completed");
        }
        
        private void transformIoBufferWrap(CtInvocation<?> invocation) {
            Factory factory = invocation.getFactory();
            
            // Create: IoBuffer.getAllocator().wrap(ByteBuffer.wrap(array))
            CtTypeAccess<?> ioBufferTypeAccess = factory.createTypeAccess(
                factory.Type().createReference("org.apache.mina.core.buffer.IoBuffer")
            );
            
            CtInvocation<?> getAllocatorCall = factory.createInvocation(
                ioBufferTypeAccess,
                factory.createExecutableReference()
                    .setDeclaringType(factory.Type().createReference("org.apache.mina.core.buffer.IoBuffer"))
                    .setSimpleName("getAllocator")
            );
            
            // Create ByteBuffer.wrap(array) call
            CtTypeAccess<?> byteBufferTypeAccess = factory.createTypeAccess(
                factory.Type().createReference("java.nio.ByteBuffer")
            );
            
            List<spoon.reflect.code.CtExpression<?>> arguments = invocation.getArguments();
            CtInvocation<?> byteBufferWrapCall = factory.createInvocation(
                byteBufferTypeAccess,
                factory.createExecutableReference()
                    .setDeclaringType(factory.Type().createReference("java.nio.ByteBuffer"))
                    .setSimpleName("wrap")
            );
            
            byteBufferWrapCall.addArgument(arguments.get(0));
            
            // Create the final wrap call
            CtInvocation<?> newWrapCall = factory.createInvocation(
                getAllocatorCall,
                factory.createExecutableReference()
                    .setDeclaringType(factory.Type().createReference("org.apache.mina.core.buffer.IoBufferAllocator"))
                    .setSimpleName("wrap")
            );
            
            newWrapCall.addArgument(byteBufferWrapCall);
            
            // Replace the original invocation
            invocation.replace(newWrapCall);
        }
    }
}