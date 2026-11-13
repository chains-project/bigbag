package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic transformation rule for Jackson 2.13.4.1 breaking change.
 * 
 * Breaking Change:
 * - Old API: writeValue(File, Object) throws IOException
 * - New API: writeValue(File, Object) throws StreamWriteException, DatabindException, IOException
 * 
 * Both StreamWriteException and DatabindException extend IOException,
 * so code that already handles IOException is compatible at runtime.
 * 
 * However, compilation fails because StreamWriteException class
 * doesn't exist in older jackson-core versions.
 * 
 * This transformation ensures methods calling writeValue()
 * properly declare throws IOException.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <input-source-dir> <output-dir>");
            System.err.println("\nThis transformation fixes Jackson 2.13.4.1 breaking change:");
            System.err.println("writeValue() now throws StreamWriteException and DatabindException");
            System.err.println("in addition to IOException.");
            System.exit(1);
        }
        
        String inputDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Jackson 2.13.4.1 Breaking Change Transformation");
        System.out.println("================================================");
        System.out.println("Input: " + inputDir);
        System.out.println("Output: " + outputDir);
        System.out.println("\nFixing: writeValue() method signature change");
        System.out.println("Old: throws IOException");
        System.out.println("New: throws StreamWriteException, DatabindException, IOException");
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Build model
        launcher.buildModel();
        
        // Find all writeValue method calls
        List<CtInvocation<?>> invocations = launcher.getModel()
            .getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    CtExecutableReference<?> exec = invocation.getExecutable();
                    if (exec == null) return false;
                    
                    // Check if method is writeValue
                    if (!"writeValue".equals(exec.getSimpleName())) {
                        return false;
                    }
                    
                    // Check if it's from Jackson ObjectWriter or ObjectMapper
                    CtTypeReference<?> declaringType = exec.getDeclaringType();
                    if (declaringType == null) return false;
                    
                    String typeName = declaringType.getQualifiedName();
                    return typeName != null && (
                        typeName.equals("com.fasterxml.jackson.databind.ObjectWriter") ||
                        typeName.equals("com.fasterxml.jackson.databind.ObjectMapper")
                    );
                }
            });
        
        System.out.println("\nFound " + invocations.size() + " writeValue() calls");
        
        int fixedMethods = 0;
        
        // For each invocation, ensure the enclosing method declares throws IOException
        for (CtInvocation<?> invocation : invocations) {
            CtMethod<?> method = invocation.getParent(CtMethod.class);
            if (method != null) {
                // Check if method already declares IOException
                boolean hasIOException = false;
                for (CtTypeReference<?> thrownType : method.getThrownTypes()) {
                    String typeName = thrownType.getQualifiedName();
                    if (typeName != null && (
                        typeName.equals("java.io.IOException") ||
                        typeName.equals("IOException")
                    )) {
                        hasIOException = true;
                        break;
                    }
                }
                
                if (!hasIOException) {
                    System.out.println("  Fixing method: " + method.getSignature());
                    System.out.println("    at: " + invocation.getPosition());
                    
                    // Add IOException to throws clause
                    CtTypeReference<Throwable> ioExceptionRef = launcher.getFactory()
                        .createCtTypeReference(java.io.IOException.class);
                    method.addThrownType(ioExceptionRef);
                    fixedMethods++;
                }
            }
        }
        
        // Apply transformations
        launcher.process();
        launcher.prettyprint();
        
        System.out.println("\nTransformation complete!");
        System.out.println("Fixed " + fixedMethods + " method(s)");
        System.out.println("\nAdditional recommendations:");
        System.out.println("1. Update jackson-core to version 2.13.4.1 or later");
        System.out.println("2. Ensure all writeValue() calls are in try-catch blocks or");
        System.out.println("   methods declare throws IOException");
        System.out.println("\nNote: StreamWriteException and DatabindException extend");
        System.out.println("JsonProcessingException which extends IOException.");
        System.out.println("Thus, handling IOException covers all new exception types.");
    }
}