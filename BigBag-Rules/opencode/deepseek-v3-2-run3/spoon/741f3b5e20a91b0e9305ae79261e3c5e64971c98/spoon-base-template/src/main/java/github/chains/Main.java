package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true); // Use no classpath to avoid missing dependencies
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("OFF"); // Reduce logging
        
        // Try to build model with error tolerance
        try {
            launcher.addInputResource(sourceDir + "/src/main/java");
        } catch (Exception e) {
            System.err.println("Warning: Could not add main/java resource, trying root directory");
            launcher.addInputResource(sourceDir);
        }
        
        CtModel model;
        try {
            model = launcher.buildModel();
        } catch (Exception e) {
            System.err.println("Error building model: " + e.getMessage());
            System.err.println("Trying with error tolerance...");
            launcher.getEnvironment().setShouldCompile(false);
            model = launcher.buildModel();
        }
        
        // Find all invocations of ObjectMapper.readValue methods
        List<CtInvocation<?>> readValueInvocations = new ArrayList<>();
        try {
            readValueInvocations = model
                .getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                    @Override
                    public boolean matches(CtInvocation<?> invocation) {
                        String methodName = invocation.getExecutable().getSimpleName();
                        return "readValue".equals(methodName);
                    }
                });
        } catch (Exception e) {
            System.err.println("Error finding readValue invocations: " + e.getMessage());
        }
        
        System.out.println("Found " + readValueInvocations.size() + " readValue() invocations");
        
        // For projects upgrading from Jackson <2.10 to >=2.13,
        // the readValue method signature changed to add StreamReadException and DatabindException.
        // This transformation adds those exceptions to the throws clause of methods calling readValue.
        
        int updatedMethods = 0;
        for (CtInvocation<?> invocation : readValueInvocations) {
            CtMethod<?> method = invocation.getParent(CtMethod.class);
            if (method != null) {
                if (updateMethodThrowsClause(method, launcher.getFactory())) {
                    updatedMethods++;
                }
            }
        }
        
        System.out.println("Updated " + updatedMethods + " method(s)");
        
        // Apply transformations
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation completed. Methods calling ObjectMapper.readValue() have been updated for Jackson 2.13+ compatibility.");
    }
    
    private static boolean updateMethodThrowsClause(CtMethod<?> method, Factory factory) {
        // Check if method already throws IOException (required for readValue)
        boolean throwsIOException = false;
        for (CtTypeReference<?> thrownType : method.getThrownTypes()) {
            if (thrownType.getQualifiedName().equals("java.io.IOException")) {
                throwsIOException = true;
                break;
            }
        }
        
        if (!throwsIOException) {
            return false;
        }
        
        // Check if method already declares the new Jackson 2.13+ exceptions
        boolean hasStreamReadException = false;
        boolean hasDatabindException = false;
        
        for (CtTypeReference<?> thrownType : method.getThrownTypes()) {
            String qualifiedName = thrownType.getQualifiedName();
            if (qualifiedName.equals("com.fasterxml.jackson.core.exc.StreamReadException")) {
                hasStreamReadException = true;
            } else if (qualifiedName.equals("com.fasterxml.jackson.databind.DatabindException")) {
                hasDatabindException = true;
            }
        }
        
        // Add missing exceptions
        boolean modified = false;
        
        if (!hasStreamReadException) {
            try {
                // Use raw type to avoid compilation issues
                CtTypeReference streamReadExceptionRef = factory.Type()
                    .createReference("com.fasterxml.jackson.core.exc.StreamReadException");
                method.addThrownType(streamReadExceptionRef);
                modified = true;
            } catch (Exception e) {
                System.err.println("Warning: Could not add StreamReadException to method " + 
                    method.getDeclaringType().getQualifiedName() + "." + method.getSimpleName());
            }
        }
        
        if (!hasDatabindException) {
            try {
                // Use raw type to avoid compilation issues
                CtTypeReference databindExceptionRef = factory.Type()
                    .createReference("com.fasterxml.jackson.databind.DatabindException");
                method.addThrownType(databindExceptionRef);
                modified = true;
            } catch (Exception e) {
                System.err.println("Warning: Could not add DatabindException to method " + 
                    method.getDeclaringType().getQualifiedName() + "." + method.getSimpleName());
            }
        }
        
        if (modified) {
            System.out.println("Updated method: " + 
                method.getDeclaringType().getQualifiedName() + "." + method.getSimpleName());
        }
        
        return modified;
    }
}