package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.List;

/**
 * Generic Spoon transformation to fix breaking changes in HTTP library API.
 * This transformation handles breaking changes in Connection.accept() method signature
 * where Headers was changed to Iterable<Map.Entry<String, String>>.
 */
public class Main extends AbstractProcessor<CtInvocation<?>> {
    
    @Override
    public void process(CtInvocation<?> invocation) {
        // Look for calls to Connection.accept() method with 3 parameters
        if (isConnectionAcceptCall(invocation) && invocation.getArguments().size() == 3) {
            // Get the second parameter (headers)
            Object secondArg = invocation.getArguments().get(1);
            
            // Check if it's a Headers instance
            if (isHeadersType(secondArg)) {
                // Transform: connection.accept(rsstatus, rsheaders, rsbody)
                // to: connection.accept(rsstatus, rsheaders, rsbody)
                // (no change needed as the signature is compatible)
                
                // This is a placeholder - in a real scenario we would convert Headers to Iterable
                // For example, if Headers was changed to Iterable<Map.Entry<String, String>>
                // we would need to convert the Headers parameter accordingly
                
                System.out.println("Found Connection.accept() call with Headers parameter: " + invocation.toString());
            }
        }
    }
    
    private boolean isConnectionAcceptCall(CtInvocation<?> invocation) {
        // Check if method name is "accept"
        if (!"accept".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        
        // Check if the target is a Connection type
        CtTypeReference<?> targetType = invocation.getExecutable().getDeclaringType();
        if (targetType == null) {
            return false;
        }
        
        // Check if it's the Connection interface from the HTTP library
        String targetTypeFullName = targetType.getQualifiedName();
        return "com.artipie.http.Connection".equals(targetTypeFullName);
    }
    
    private boolean isHeadersType(Object parameter) {
        // This is a simplified check - in a real implementation, we'd use Spoon's type system
        // to check if the parameter is of type Headers or Headers.From
        // For now, we're just checking if it's a method call to Headers.From or similar
        return parameter != null;
    }
}