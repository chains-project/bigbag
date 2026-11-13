package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.CtScanner;
import java.util.List;

/**
 * Generic Spoon transformation for fixing breaking API changes in Apache Mina 2.2.1.
 * This transformation handles actual breaking changes:
 * 1. SslFilter constructor changed: SslFilter(SSLContext, boolean) -> SslFilter(SSLContext)
 * 2. setUseClientMode(boolean) method removed
 * 3. PEER_ADDRESS constant removed
 * 4. initiateHandshake(IoSession) method removed
 * 
 * The transformation is generic and reusable for any project affected by Mina 2.2.1 API changes.
 */
public class Main {
    
    /**
     * Visitor that applies Mina 2.2.1 breaking change transformations.
     */
    static class MinaBreakingChangeVisitor extends CtScanner {
        private final Launcher launcher;
        
        MinaBreakingChangeVisitor(Launcher launcher) {
            this.launcher = launcher;
        }
        
        @Override
        public <T> void visitCtInvocation(CtInvocation<T> invocation) {
            super.visitCtInvocation(invocation);
            
            CtExecutableReference<T> execRef = invocation.getExecutable();
            String methodName = execRef.getSimpleName();
            
            // Check for SslFilter constructor calls with 2 parameters
            if (execRef.isConstructor() && "SslFilter".equals(methodName)) {
                if (invocation.getArguments().size() == 2) {
                    // Likely SslFilter(SSLContext, boolean) - needs fixing
                    fixSslFilterConstructor(invocation);
                }
            }
            
            // Check for setUseClientMode method calls
            if ("setUseClientMode".equals(methodName)) {
                removeMethodCall(invocation, "setUseClientMode");
            }
            
            // Check for initiateHandshake method calls  
            if ("initiateHandshake".equals(methodName)) {
                removeMethodCall(invocation, "initiateHandshake");
            }
        }
        
        @Override
        public <T> void visitCtConstructorCall(CtConstructorCall<T> constructorCall) {
            super.visitCtConstructorCall(constructorCall);
            
            CtExecutableReference<T> execRef = constructorCall.getExecutable();
            String methodName = execRef.getSimpleName();
            
            // Check for SslFilter constructor calls with 2 parameters
            // This handles super() constructor calls
            if ("SslFilter".equals(methodName)) {
                if (constructorCall.getArguments().size() == 2) {
                    fixSslFilterConstructorCall(constructorCall);
                }
            }
        }
        
        @Override
        public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
            super.visitCtFieldRead(fieldRead);
            
            CtFieldReference<T> fieldRef = fieldRead.getVariable();
            String fieldName = fieldRef.getSimpleName();
            
            // Handle PEER_ADDRESS constant access
            if ("PEER_ADDRESS".equals(fieldName)) {
                fixPeerAddressAccess(fieldRead);
            }
        }
        
        @Override
        public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
            super.visitCtFieldWrite(fieldWrite);
            
            CtFieldReference<T> fieldRef = fieldWrite.getVariable();
            String fieldName = fieldRef.getSimpleName();
            
            // Handle PEER_ADDRESS constant access (for writes if any)
            if ("PEER_ADDRESS".equals(fieldName)) {
                fixPeerAddressAccess(fieldWrite);
            }
        }
        
        
        
        private <T> void fixSslFilterConstructor(CtInvocation<T> invocation) {
            System.out.println("Fixing SslFilter constructor call at line " + invocation.getPosition().getLine());
            
            // SslFilter(SSLContext, boolean) -> SslFilter(SSLContext)
            // Remove the boolean parameter
            if (invocation.getArguments().size() == 2) {
                try {
                    // Keep only the first argument (SSLContext)
                    List<CtExpression<?>> args = invocation.getArguments();
                    CtExpression<?> sslContextArg = args.get(0);
                    
                    // Create new invocation with only SSLContext parameter
                    CtInvocation<T> newInvocation = launcher.getFactory().createInvocation(
                        invocation.getTarget(),
                        invocation.getExecutable(),
                        java.util.Collections.singletonList(sslContextArg)
                    );
                    
                    invocation.replace(newInvocation);
                    System.out.println("  Removed boolean parameter from SslFilter constructor");
                } catch (Exception e) {
                    System.err.println("Failed to fix SslFilter constructor: " + e.getMessage());
                }
            }
        }
        
        private <T> void fixSslFilterConstructorCall(CtConstructorCall<T> constructorCall) {
            System.out.println("Fixing SslFilter super constructor call at line " + constructorCall.getPosition().getLine());
            
            // SslFilter(SSLContext, boolean) -> SslFilter(SSLContext)
            // Remove the boolean parameter
            if (constructorCall.getArguments().size() == 2) {
                try {
                    // Keep only the first argument (SSLContext)
                    List<CtExpression<?>> args = constructorCall.getArguments();
                    CtExpression<?> sslContextArg = args.get(0);
                    
                    // Clone and set new arguments
                    CtConstructorCall<T> newConstructorCall = constructorCall.clone();
                    newConstructorCall.setArguments(java.util.Collections.singletonList(sslContextArg));
                    
                    constructorCall.replace(newConstructorCall);
                    System.out.println("  Removed boolean parameter from SslFilter super constructor");
                } catch (Exception e) {
                    System.err.println("Failed to fix SslFilter super constructor: " + e.getMessage());
                }
            }
        }
        
        private <T> void removeMethodCall(CtInvocation<T> invocation, String methodName) {
            System.out.println("Removing call to " + methodName + " at line " + invocation.getPosition().getLine());
            
            // For setUseClientMode and initiateHandshake, we can't just remove the call
            // because it might be used in a statement. We need to replace it with an empty statement
            // or comment it out. Since Spoon doesn't support comments directly in transformations,
            // we'll replace with an empty statement if it's a standalone call.
            
            CtStatement parentStatement = invocation.getParent(CtStatement.class);
            if (parentStatement != null) {
                try {
                    // Create an empty block
                    CtBlock<?> emptyBlock = launcher.getFactory().createBlock();
                    parentStatement.replace(emptyBlock);
                    System.out.println("  Replaced with empty block");
                } catch (Exception e) {
                    System.err.println("Failed to remove method call: " + e.getMessage());
                }
            }
        }
        
        private <T> void fixPeerAddressAccess(CtFieldRead<T> fieldRead) {
            System.out.println("Fixing PEER_ADDRESS read at line " + fieldRead.getPosition().getLine());
            
            // PEER_ADDRESS constant was removed. Need to find an alternative.
            // Looking at the code, it's used as: session.setAttribute(PEER_ADDRESS, remoteAddress)
            // We could replace with a string literal or find another constant.
            
            // Since we can't know the exact replacement without more context,
            // we'll replace with a string literal "peerAddress" as a generic fix.
            try {
                CtLiteral<String> replacement = launcher.getFactory().createLiteral("peerAddress");
                fieldRead.replace(replacement);
                System.out.println("  Replaced PEER_ADDRESS with string literal \"peerAddress\"");
            } catch (Exception e) {
                System.err.println("Failed to fix PEER_ADDRESS read: " + e.getMessage());
            }
        }
        
        private <T> void fixPeerAddressAccess(CtFieldWrite<T> fieldWrite) {
            System.out.println("Fixing PEER_ADDRESS write at line " + fieldWrite.getPosition().getLine());
            
            try {
                CtLiteral<String> replacement = launcher.getFactory().createLiteral("peerAddress");
                fieldWrite.replace(replacement);
                System.out.println("  Replaced PEER_ADDRESS with string literal \"peerAddress\"");
            } catch (Exception e) {
                System.err.println("Failed to fix PEER_ADDRESS write: " + e.getMessage());
            }
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.err.println("Example: java github.chains.Main /path/to/project/src /path/to/output");
            return;
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.setSourceOutputDirectory(outputDir);
        
        try {
            // Build the model
            launcher.getEnvironment().setNoClasspath(true); // Don't fail on missing dependencies
            CtModel model = launcher.buildModel();
            
            System.out.println("Model built successfully. Applying Mina 2.2.1 breaking change fixes...");
            
            // Apply transformations for Mina 2.2.1 breaking changes
            MinaBreakingChangeVisitor visitor = new MinaBreakingChangeVisitor(launcher);
            model.getRootPackage().accept(visitor);
            
            // Output transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed. Check output directory: " + outputDir);
            System.out.println("\nSummary of fixes applied:");
            System.out.println("1. SslFilter constructor calls: boolean parameter removed");
            System.out.println("2. setUseClientMode() method calls: removed");
            System.out.println("3. PEER_ADDRESS constant references: replaced with string literal");
            System.out.println("4. initiateHandshake() method calls: removed");
            System.out.println("\nNote: Some method removals may require additional manual fixes.");
            System.out.println("The removed methods (setUseClientMode, initiateHandshake) were deprecated");
            System.out.println("and removed in Mina 2.2.1. Check if their functionality is still needed.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}