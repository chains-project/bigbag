package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.err.println("\nThis transformation fixes Dubbo 2.7.21 API breaking changes:");
            System.err.println("1. RpcContext.setFuture() now requires CompletableFuture<?> instead of Future");
            System.err.println("2. FutureContext.setCompatibleFuture() now requires CompletableFuture<?> instead of Future");
            System.err.println("\nTransformations applied:");
            System.err.println("- RpcContext.setFuture(asyncRpcResult) -> RpcContext.setFuture(new FutureAdapter<>(asyncRpcResult.getResponseFuture()))");
            System.err.println("- FutureContext.setCompatibleFuture(asyncRpcResult) -> FutureContext.setCompatibleFuture(asyncRpcResult.getResponseFuture())");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Dubbo 2.7.21 API fix to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            int fixesApplied = 0;
            
            // Find all method invocations
            List<CtInvocation<?>> allInvocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
            
            for (CtInvocation<?> invocation : allInvocations) {
                String methodName = invocation.getExecutable().getSimpleName();
                
                // Check for RpcContext.setFuture(AsyncRpcResult)
                if ("setFuture".equals(methodName) && invocation.getArguments().size() == 1) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null) {
                        String targetStr = target.toString();
                        // Match any RpcContext.setFuture call (could be RpcContext.getContext().setFuture or similar)
                        if (targetStr.contains("RpcContext")) {
                            
                            CtExpression<?> arg = invocation.getArguments().get(0);
                            String argStr = arg.toString();
                            
                            // Check if argument is AsyncRpcResult (by variable name or type)
                            // This is a generic pattern that should work for any project
                            boolean isAsyncRpcResult = argStr.matches(".*[Aa]sync[Rr]pc[Rr]esult.*") ||
                                                      (arg.getType() != null && 
                                                       arg.getType().getQualifiedName() != null &&
                                                       arg.getType().getQualifiedName().contains("AsyncRpcResult"));
                            
                            if (isAsyncRpcResult) {
                                System.out.println("Found RpcContext.setFuture call with AsyncRpcResult at: " + 
                                                  invocation.getPosition());
                                
                                // Create getResponseFuture() invocation
                                CtInvocation<?> getResponseFuture = launcher.getFactory().Code().createInvocation(
                                    arg.clone(),
                                    launcher.getFactory().createExecutableReference().setSimpleName("getResponseFuture")
                                );
                                
                                // Create FutureAdapter constructor call
                                CtConstructorCall<?> futureAdapter = launcher.getFactory().Code().createConstructorCall(
                                    launcher.getFactory().Type().createReference("org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter")
                                );
                                futureAdapter.addArgument(getResponseFuture);
                                
                                // Replace the argument
                                invocation.getArguments().set(0, futureAdapter);
                                fixesApplied++;
                                System.out.println("Fixed: wrapped AsyncRpcResult in FutureAdapter");
                            }
                        }
                    }
                }
                
                // Check for FutureContext.setCompatibleFuture(AsyncRpcResult)
                if ("setCompatibleFuture".equals(methodName) && invocation.getArguments().size() == 1) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null) {
                        String targetStr = target.toString();
                        if (targetStr.contains("FutureContext")) {
                            
                            CtExpression<?> arg = invocation.getArguments().get(0);
                            String argStr = arg.toString();
                            
                            // Check if argument is AsyncRpcResult
                            boolean isAsyncRpcResult = argStr.matches(".*[Aa]sync[Rr]pc[Rr]esult.*") ||
                                                      (arg.getType() != null && 
                                                       arg.getType().getQualifiedName() != null &&
                                                       arg.getType().getQualifiedName().contains("AsyncRpcResult"));
                            
                            if (isAsyncRpcResult) {
                                System.out.println("Found FutureContext.setCompatibleFuture call with AsyncRpcResult at: " + 
                                                  invocation.getPosition());
                                
                                // Create getResponseFuture() invocation
                                CtInvocation<?> getResponseFuture = launcher.getFactory().Code().createInvocation(
                                    arg.clone(),
                                    launcher.getFactory().createExecutableReference().setSimpleName("getResponseFuture")
                                );
                                
                                // Replace the argument
                                invocation.getArguments().set(0, getResponseFuture);
                                fixesApplied++;
                                System.out.println("Fixed: replaced AsyncRpcResult with getResponseFuture()");
                            }
                        }
                    }
                }
            }
            
            System.out.println("\nApplied " + fixesApplied + " fixes.");
            
            if (fixesApplied > 0) {
                // Write modified files
                launcher.setSourceOutputDirectory(sourceDir + "/../transformed");
                launcher.prettyprint();
                System.out.println("Transformed code written to: " + sourceDir + "/../transformed");
                
                // Also add import for FutureAdapter if not present
                System.out.println("\nNote: May need to add import for org.apache.dubbo.rpc.protocol.dubbo.FutureAdapter");
            } else {
                System.out.println("No fixes needed.");
            }
            
        } catch (Exception e) {
            System.err.println("Error applying transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}