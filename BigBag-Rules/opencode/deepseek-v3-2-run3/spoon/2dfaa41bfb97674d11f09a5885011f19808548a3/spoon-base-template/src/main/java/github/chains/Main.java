package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformer.jar <source-directory>");
            System.err.println("Example: java -jar spoon-transformer.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourcePath = args[0];
        System.out.println("Transforming source directory: " + sourcePath);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourcePath);
        
        // Build the model
        launcher.buildModel();
        
        // Find all method invocations
        List<CtInvocation> invocations = launcher.getModel()
            .getRootPackage()
            .getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invocation) {
                    // Check if this is a getArgumentAt method call
                    CtExecutableReference execRef = invocation.getExecutable();
                    if (execRef == null) {
                        return false;
                    }
                    
                    String methodName = execRef.getSimpleName();
                    return "getArgumentAt".equals(methodName);
                }
            });
        
        System.out.println("Found " + invocations.size() + " occurrences of getArgumentAt()");
        
        // Transform each invocation
        for (CtInvocation invocation : invocations) {
            System.out.println("Transforming: " + invocation.getPosition());
            
            // Create new method reference for getArgument
            CtExecutableReference oldExecRef = invocation.getExecutable();
            
            // Create a copy with new name using the same factory
            CtExecutableReference newExecRef = launcher.getFactory().Core().createExecutableReference();
            newExecRef.setSimpleName("getArgument");
            newExecRef.setDeclaringType(oldExecRef.getDeclaringType());
            newExecRef.setStatic(oldExecRef.isStatic());
            
            // Set the new executable reference on the invocation
            invocation.setExecutable(newExecRef);
        }
        
        // Output transformed code
        launcher.setSourceOutputDirectory(sourcePath + "-transformed");
        launcher.prettyprint();
        
        System.out.println("Transformation complete. Output written to: " + sourcePath + "-transformed");
    }
}