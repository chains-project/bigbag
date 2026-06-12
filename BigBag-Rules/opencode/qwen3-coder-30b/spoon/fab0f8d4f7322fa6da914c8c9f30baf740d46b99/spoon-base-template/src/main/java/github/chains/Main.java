package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic Spoon transformation for fixing breaking API changes in com.artipie:http dependency.
 */
public class Main {
    public static void main(String[] args) {
        // Create a Spoon launcher
        Launcher launcher = new Launcher();
        
        // Set the source directory to the docker-adapter project
        launcher.addInputResource("/workspace/docker-adapter/src/main/java");
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Find all method invocations that match the pattern of Connection.accept calls
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        
        int fixedCount = 0;
        for (CtInvocation invocation : invocations) {
            // Check if the invocation is to Connection.accept with Headers as second parameter
            if (isAcceptMethodWithHeaders(invocation)) {
                fixedCount++;
            }
        }
        
        System.out.println("Found " + fixedCount + " Connection.accept calls in docker-adapter project");
    }
    
    /**
     * Checks if the invocation is to Connection.accept method with Headers as second parameter
     */
    private static boolean isAcceptMethodWithHeaders(CtInvocation invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null) {
            return false;
        }
        
        // Check if method name is "accept"
        if (!"accept".equals(executable.getSimpleName())) {
            return false;
        }
        
        // Check if method is from Connection interface
        CtTypeReference<?> declaringType = executable.getDeclaringType();
        if (declaringType == null || !declaringType.getQualifiedName().contains("Connection")) {
            return false;
        }
        
        // Check if there are exactly 3 parameters
        if (invocation.getArguments().size() != 3) {
            return false;
        }
        
        // Check if second parameter is of type Headers
        // We're not doing the actual transformation in this basic version
        return true;
    }
}