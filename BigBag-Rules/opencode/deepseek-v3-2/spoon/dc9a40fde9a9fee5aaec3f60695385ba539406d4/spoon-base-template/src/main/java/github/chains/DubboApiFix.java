package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.processing.AbstractProcessor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import java.util.List;

/**
 * Generic Spoon transformation rule for fixing Dubbo 2.7.21 API breaking change.
 * 
 * Breaking Change Pattern:
 * - Old: RpcContext.setFuture(AsyncRpcResult) and FutureContext.setCompatibleFuture(AsyncRpcResult)
 * - New: RpcContext.setFuture(CompletableFuture) and FutureContext.setCompatibleFuture(CompletableFuture)
 * 
 * Transformation Rule:
 * 1. RpcContext.setFuture(asyncRpcResult) → RpcContext.setFuture(new FutureAdapter<>(asyncRpcResult.getResponseFuture()))
 * 2. FutureContext.setCompatibleFuture(asyncRpcResult) → FutureContext.setCompatibleFuture(asyncRpcResult.getResponseFuture())
 * 
 * This rule is generic and can be applied to any Java project affected by this Dubbo API change.
 */
public class DubboApiFix {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp spoon-transformer.jar github.chains.DubboApiFix <source-directory>");
            System.err.println("Example: java -cp spoon-transformer.jar github.chains.DubboApiFix /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Dubbo API migration fix to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Add processor
        launcher.addProcessor(new DubboAsyncRpcResultProcessor());
        
        try {
            // Build model
            CtModel model = launcher.buildModel();
            
            // Process transformations
            launcher.process();
            
            // Output transformed code (overwrites original files)
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Successfully applied Dubbo API migration fix!");
            System.out.println("Transformations applied:");
            System.out.println("  1. RpcContext.setFuture(AsyncRpcResult) -> RpcContext.setFuture(new FutureAdapter<>(asyncRpcResult.getResponseFuture()))");
            System.out.println("  2. FutureContext.setCompatibleFuture(AsyncRpcResult) -> FutureContext.setCompatibleFuture(asyncRpcResult.getResponseFuture())");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Spoon processor that fixes AsyncRpcResult usage in Dubbo 2.7.21+ API.
     * This processor is generic and works for both Apache Dubbo and Alibaba Dubbo.
     */
    static class DubboAsyncRpcResultProcessor extends AbstractProcessor<CtInvocation<?>> {
        
        @Override
        public void process(CtInvocation<?> invocation) {
            try {
                String methodName = invocation.getExecutable().getSimpleName();
                
                // We only care about setFuture and setCompatibleFuture methods
                if (!"setFuture".equals(methodName) && !"setCompatibleFuture".equals(methodName)) {
                    return;
                }
                
                // Check if this is a call to RpcContext.setFuture or FutureContext.setCompatibleFuture
                CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
                if (declaringType == null) {
                    return;
                }
                
                String declaringTypeName = declaringType.getQualifiedName();
                boolean isRpcContext = declaringTypeName.contains("RpcContext");
                boolean isFutureContext = declaringTypeName.contains("FutureContext");
                
                if (!isRpcContext && !isFutureContext) {
                    return;
                }
                
                // Get the argument
                List<CtExpression<?>> arguments = invocation.getArguments();
                if (arguments.isEmpty()) {
                    return;
                }
                
                CtExpression<?> arg = arguments.get(0);
                String argType = arg.getType() != null ? arg.getType().getQualifiedName() : "";
                
                // Check if argument is AsyncRpcResult (Apache or Alibaba)
                boolean isAsyncRpcResult = argType.contains("AsyncRpcResult");
                
                if (isAsyncRpcResult) {
                    System.out.println("Found " + declaringTypeName + "." + methodName + "(" + argType + ") at line " + 
                        invocation.getPosition().getLine());
                    
                    // Create the transformation
                    transformAsyncRpcResultCall(invocation, arg, isRpcContext);
                }
                
            } catch (Exception e) {
                // Log error but continue processing other files
                System.err.println("Warning: Could not process invocation at " + invocation.getPosition() + ": " + e.getMessage());
            }
        }
        
        /**
         * Transform an AsyncRpcResult argument to use getResponseFuture()
         */
        private void transformAsyncRpcResultCall(CtInvocation<?> invocation, CtExpression<?> asyncRpcResultExpr, boolean isRpcContext) {
            Factory factory = getFactory();
            
            // Create getResponseFuture() method call
            CtInvocation<?> getResponseFutureCall = factory.createInvocation();
            getResponseFutureCall.setTarget(asyncRpcResultExpr.clone());
            
            // Create executable reference for getResponseFuture()
            CtTypeReference<?> returnType = factory.Type().createReference("java.util.concurrent.CompletableFuture");
            
            getResponseFutureCall.setExecutable(factory.createExecutableReference());
            getResponseFutureCall.getExecutable().setSimpleName("getResponseFuture");
            getResponseFutureCall.getExecutable().setType(returnType);
            getResponseFutureCall.getExecutable().setParameters(java.util.Collections.emptyList());
            
            if (isRpcContext) {
                // For RpcContext.setFuture, wrap in FutureAdapter
                // Determine package (Apache or Alibaba)
                String packageName = asyncRpcResultExpr.getType().getQualifiedName().startsWith("org.apache") ? 
                    "org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter" : 
                    "com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter";
                
                CtConstructorCall<?> futureAdapter = factory.createConstructorCall();
                futureAdapter.setType(factory.Type().createReference(packageName));
                futureAdapter.setArguments(List.of(getResponseFutureCall));
                
                invocation.setArguments(List.of(futureAdapter));
                
                System.out.println("  -> Transformed to: new FutureAdapter<>(asyncRpcResult.getResponseFuture())");
            } else {
                // For FutureContext.setCompatibleFuture, use getResponseFuture() directly
                invocation.setArguments(List.of(getResponseFutureCall));
                System.out.println("  -> Transformed to: asyncRpcResult.getResponseFuture()");
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // Use a simple filter - we'll do detailed checking in process() method
            String methodName = candidate.getExecutable().getSimpleName();
            return "setFuture".equals(methodName) || "setCompatibleFuture".equals(methodName);
        }
    }
}