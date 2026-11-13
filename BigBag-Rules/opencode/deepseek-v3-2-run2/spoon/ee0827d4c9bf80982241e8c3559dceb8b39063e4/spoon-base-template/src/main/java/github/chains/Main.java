package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.sniper.SniperJavaPrettyPrinter;
import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar spoon-transform.jar <sourceDir> <outputDir>");
            System.err.println("Example: java -jar spoon-transform.jar /path/to/src /path/to/transformed");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        // Create launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        
        // Use sniper mode to preserve formatting
        launcher.getEnvironment().setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(launcher.getEnvironment()));
        
        // Add input resource
        launcher.addInputResource(sourceDir);
        
        // Add processor for method renaming
        launcher.addProcessor(new MethodRenameProcessor(
            "org.apache.commons.io.input.CountingInputStream",
            "getByteCount",
            "getCount"
        ));
        
        // Add processor for handling constructor calls to missing classes
        launcher.addProcessor(new MissingClassConstructorProcessor());
        
        // Add processor for handling field accesses to missing classes
        launcher.addProcessor(new MissingClassFieldAccessProcessor());
        
        // Add processor for handling class references (imports, extends, etc.)
        launcher.addProcessor(new MissingClassReferenceProcessor());
        
        // Set output directory
        launcher.setSourceOutputDirectory(outputDir);
        
        // Run transformation
        launcher.run();
        
        System.out.println("Transformation complete!");
        System.out.println("\n=== SUMMARY OF BREAKING CHANGES FOUND ===");
        System.out.println("1. Method rename: CountingInputStream.getByteCount() -> CountingInputStream.getCount()");
        System.out.println("2. Missing classes that need replacement:");
        System.out.println("   - org.apache.commons.io.input.BoundedInputStream");
        System.out.println("   - org.apache.commons.io.input.ClosedInputStream");
        System.out.println("   - org.apache.commons.io.output.ThresholdingOutputStream");
        System.out.println("   - org.apache.commons.io.output.NullPrintStream");
        System.out.println("\nThese classes are not in the new API specification and must be replaced.");
    }
    
    /**
     * Generic processor for renaming a method in a specific class.
     * This handles the pattern: ClassX.methodY() -> ClassX.methodZ()
     */
    static class MethodRenameProcessor extends AbstractProcessor<CtInvocation<?>> {
        private final String targetClassName;
        private final String oldMethodName;
        private final String newMethodName;
        
        public MethodRenameProcessor(String targetClassName, String oldMethodName, String newMethodName) {
            this.targetClassName = targetClassName;
            this.oldMethodName = oldMethodName;
            this.newMethodName = newMethodName;
        }
        
        @Override
        public void process(CtInvocation<?> invocation) {
            // Check if this is a method invocation
            if (invocation.getExecutable() == null) {
                return;
            }
            
            // Check if method name matches old method name
            if (!oldMethodName.equals(invocation.getExecutable().getSimpleName())) {
                return;
            }
            
            // Get the target expression (what the method is called on)
            var target = invocation.getTarget();
            if (target == null) {
                return;
            }
            
            // Try to get the type of the target
            var targetType = target.getType();
            if (targetType == null) {
                return;
            }
            
            // Check if target type matches or is subclass of target class
            String typeName = targetType.getQualifiedName();
            if (typeName != null && typeName.equals(targetClassName)) {
                // Rename the method
                invocation.getExecutable().setSimpleName(newMethodName);
                System.out.println("[FIXED] Renamed " + oldMethodName + "() to " + newMethodName + "() on " + typeName + " at " + invocation.getPosition());
            }
        }
    }
    
    /**
     * Processor for detecting constructor calls to missing classes.
     * This handles patterns like: new MissingClass(...)
     */
    static class MissingClassConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        // List of known missing classes from the new API
        private static final List<String> MISSING_CLASSES = List.of(
            "org.apache.commons.io.input.BoundedInputStream",
            "org.apache.commons.io.input.ClosedInputStream",
            "org.apache.commons.io.output.ThresholdingOutputStream",
            "org.apache.commons.io.output.NullPrintStream"
        );
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> typeRef = constructorCall.getType();
            if (typeRef == null) {
                return;
            }
            
            String typeName = typeRef.getQualifiedName();
            if (typeName != null && MISSING_CLASSES.contains(typeName)) {
                System.out.println("[ERROR] Found constructor call to missing class: " + typeName + " at " + constructorCall.getPosition());
                System.out.println("   Constructor arguments: " + constructorCall.getArguments().size() + " argument(s)");
                System.out.println("   This class is not available in the new API. Need to replace with alternative.");
                
                // Additional info based on class
                if (typeName.equals("org.apache.commons.io.input.BoundedInputStream")) {
                    System.out.println("   BoundedInputStream was used to limit bytes read from a stream.");
                    System.out.println("   Consider implementing custom wrapper or using different approach.");
                } else if (typeName.equals("org.apache.commons.io.input.ClosedInputStream")) {
                    System.out.println("   ClosedInputStream was a singleton representing an already-closed stream.");
                    System.out.println("   Consider using custom implementation or NullInputStream if available.");
                }
            }
        }
    }
    
    /**
     * Processor for detecting field accesses to missing classes.
     * This handles patterns like: MissingClass.STATIC_FIELD
     */
    static class MissingClassFieldAccessProcessor extends AbstractProcessor<CtFieldRead<?>> {
        private static final List<String> MISSING_CLASSES = List.of(
            "org.apache.commons.io.input.BoundedInputStream",
            "org.apache.commons.io.input.ClosedInputStream",
            "org.apache.commons.io.output.ThresholdingOutputStream",
            "org.apache.commons.io.output.NullPrintStream"
        );
        
        @Override
        public void process(CtFieldRead<?> fieldRead) {
            // Check if this is a static field access on a missing class
            var target = fieldRead.getTarget();
            if (target instanceof CtTypeAccess) {
                CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) target;
                CtTypeReference<?> typeRef = typeAccess.getAccessedType();
                if (typeRef != null) {
                    String typeName = typeRef.getQualifiedName();
                    if (typeName != null && MISSING_CLASSES.contains(typeName)) {
                        System.out.println("[ERROR] Found field access on missing class: " + typeName + "." + fieldRead.getVariable().getSimpleName() + " at " + fieldRead.getPosition());
                        System.out.println("   This class is not available in the new API.");
                        
                        if (typeName.equals("org.apache.commons.io.output.NullPrintStream")) {
                            System.out.println("   NullPrintStream.NULL_PRINT_STREAM was a null-output PrintStream.");
                            System.out.println("   Consider using System.out or custom null PrintStream implementation.");
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Processor for detecting class references (extends, implements, type references).
     * This handles patterns like: class X extends MissingClass
     */
    static class MissingClassReferenceProcessor extends AbstractProcessor<CtClass<?>> {
        private static final List<String> MISSING_CLASSES = List.of(
            "org.apache.commons.io.input.BoundedInputStream",
            "org.apache.commons.io.input.ClosedInputStream",
            "org.apache.commons.io.output.ThresholdingOutputStream",
            "org.apache.commons.io.output.NullPrintStream"
        );
        
        @Override
        public void process(CtClass<?> ctClass) {
            // Check superclass
            CtTypeReference<?> superClass = ctClass.getSuperclass();
            if (superClass != null) {
                String superClassName = superClass.getQualifiedName();
                if (superClassName != null && MISSING_CLASSES.contains(superClassName)) {
                    System.out.println("[ERROR] Class extends missing class: " + ctClass.getQualifiedName() + " extends " + superClassName + " at " + ctClass.getPosition());
                    System.out.println("   This breaks inheritance hierarchy. Need to change superclass or reimplement.");
                    
                    if (superClassName.equals("org.apache.commons.io.output.ThresholdingOutputStream")) {
                        System.out.println("   ThresholdingOutputStream was an abstract class for output streams with threshold.");
                        System.out.println("   Consider implementing threshold logic directly or find alternative base class.");
                    }
                }
            }
            
            // Check implemented interfaces (though these classes are not interfaces)
            // Could also check field types, method parameter types, etc.
        }
    }
}