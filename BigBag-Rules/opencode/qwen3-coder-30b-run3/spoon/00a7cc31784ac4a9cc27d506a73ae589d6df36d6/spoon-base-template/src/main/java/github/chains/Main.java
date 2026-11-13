package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in MINA dependency.
 * Specifically addresses changes in SSLFilter API from version 2.0 to 2.2.1
 */
public class Main {
    public static void main(String[] args) {
        // Configuration parameters - these should be adjusted based on target project
        String[] inputPaths = {"/workspace/quickfixj"};
        String outputPath = "/workspace/quickfixj-fixed";
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(String.join(",", inputPaths));
        launcher.setSourceOutputDirectory(outputPath);
        
        // Process the code
        CtModel model = launcher.buildModel();
        
        // Count how many fixes we've made
        int fixesMade = 0;
        
        // Fix 1: Remove boolean parameter from SslFilter constructor calls
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall call : constructorCalls) {
            if (call.getType().getQualifiedName().contains("SslFilter")) {
                // Check if this constructor call has more than one argument
                if (call.getArguments().size() > 1) {
                    // Remove the last argument (boolean)
                    try {
                        call.getArguments().remove(call.getArguments().size() - 1);
                        fixesMade++;
                    } catch (Exception e) {
                        // Skip this one if it fails
                    }
                }
            }
        }
        
        // Fix 2: Remove calls to setUseClientMode method
        List<CtInvocation> setUseClientModeCalls = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation invocation : setUseClientModeCalls) {
            CtExecutableReference executableRef = invocation.getExecutable();
            if (executableRef.getSimpleName().equals("setUseClientMode")) {
                // Remove the call entirely as it's no longer needed
                invocation.delete();
                fixesMade++;
            }
        }
        
        // Fix 3: Remove PEER_ADDRESS constant references
        List<CtFieldRead> peerAddressRefs = model.getElements(new TypeFilter<>(CtFieldRead.class));
        for (CtFieldRead fieldRead : peerAddressRefs) {
            if (fieldRead.getVariable().getSimpleName().equals("PEER_ADDRESS")) {
                // Delete the field read 
                fieldRead.delete();
                fixesMade++;
            }
        }
        
        // Fix 4: Remove initiateHandshake method calls
        List<CtInvocation> handshakeCalls = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation invocation : handshakeCalls) {
            CtExecutableReference executableRef = invocation.getExecutable();
            if (executableRef.getSimpleName().equals("initiateHandshake")) {
                // Remove the method call as it no longer exists
                invocation.delete();
                fixesMade++;
            }
        }
        
        // Generate the modified code
        launcher.setSourceOutputDirectory(outputPath);
        launcher.process();
        
        System.out.println("Transformation completed. Made " + fixesMade + " fixes. Fixed files are in: " + outputPath);
    }
}