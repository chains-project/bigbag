package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.visitor.ProcessingVisitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic Spoon transformation for handling breaking dependency updates.
 * This transformation can be configured to handle various breaking changes:
 * 1. Method signature changes (renames, parameter changes, return type changes)
 * 2. Class removals (with suggested replacements)
 * 3. Field removals (with replacement patterns)
 * 
 * The transformation is parameterized and can be applied to any project
 * affected by similar breaking changes in dependencies.
 */
public class GenericBreakingChangeTransformer {
    
    // Configuration for method changes
    private static final Map<String, String> METHOD_TRANSFORMATIONS = new HashMap<>();
    static {
        // Old method signature -> New method signature
        METHOD_TRANSFORMATIONS.put(
            "org.apache.commons.io.input.CountingInputStream.getByteCount()",
            "org.apache.commons.io.input.CountingInputStream.getCount()"
        );
    }
    
    // Configuration for removed classes and their suggested replacements
    private static final Map<String, ReplacementInfo> CLASS_REPLACEMENTS = new HashMap<>();
    static {
        CLASS_REPLACEMENTS.put(
            "org.apache.commons.io.input.BoundedInputStream",
            new ReplacementInfo(
                "BoundedInputStream was removed. Implement custom wrapper or use pattern: " +
                "InputStream.read(byte[], 0, Math.min(limit, available))",
                null, // No direct replacement
                "Custom implementation required"
            )
        );
        
        CLASS_REPLACEMENTS.put(
            "org.apache.commons.io.input.ClosedInputStream",
            new ReplacementInfo(
                "ClosedInputStream was removed. Replace with empty ByteArrayInputStream",
                "java.io.ByteArrayInputStream",
                "new ByteArrayInputStream(new byte[0])"
            )
        );
        
        CLASS_REPLACEMENTS.put(
            "org.apache.commons.io.output.ThresholdingOutputStream",
            new ReplacementInfo(
                "ThresholdingOutputStream was removed. Complete rewrite required",
                null,
                "Custom implementation required"
            )
        );
        
        CLASS_REPLACEMENTS.put(
            "org.apache.commons.io.output.NullPrintStream",
            new ReplacementInfo(
                "NullPrintStream was removed. Create custom implementation",
                null,
                "Custom NullPrintStream implementation"
            )
        );
    }
    
    // Configuration for field changes
    private static final Map<String, String> FIELD_TRANSFORMATIONS = new HashMap<>();
    static {
        // Old field access -> Replacement pattern
        FIELD_TRANSFORMATIONS.put(
            "org.apache.commons.io.output.NullPrintStream.NULL_PRINT_STREAM",
            "new NullPrintStream()"
        );
    }
    
    static class ReplacementInfo {
        String description;
        String replacementClass;
        String replacementPattern;
        
        ReplacementInfo(String description, String replacementClass, String replacementPattern) {
            this.description = description;
            this.replacementClass = replacementClass;
            this.replacementPattern = replacementPattern;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.err.println("\nThis transformation handles commons-io breaking changes:");
            System.err.println("1. CountingInputStream.getByteCount() -> getCount()");
            System.err.println("2. Removed classes: BoundedInputStream, ClosedInputStream,");
            System.err.println("   ThresholdingOutputStream, NullPrintStream");
            System.err.println("3. Field removal: NullPrintStream.NULL_PRINT_STREAM");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying generic breaking change transformations to: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        applyMethodTransformations(model);
        detectRemovedClasses(model);
        detectFieldAccessIssues(model);
        
        // Output the transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("\n=== SUMMARY ===");
        System.out.println("Transformations applied:");
        System.out.println("- Method renames: " + METHOD_TRANSFORMATIONS.size());
        System.out.println("- Classes checked for removal: " + CLASS_REPLACEMENTS.size());
        System.out.println("- Fields checked: " + FIELD_TRANSFORMATIONS.size());
        System.out.println("\nNote: Some changes require manual fixes (see warnings above).");
        System.out.println("The transformation provides guidance but cannot automatically");
        System.out.println("fix all breaking changes due to semantic differences.");
    }
    
    private static void applyMethodTransformations(CtModel model) {
        System.out.println("\n=== Applying method transformations ===");
        
        for (Map.Entry<String, String> entry : METHOD_TRANSFORMATIONS.entrySet()) {
            String oldMethod = entry.getKey();
            String newMethod = entry.getValue();
            
            // Parse old method signature
            String[] oldParts = parseMethodSignature(oldMethod);
            String oldClassName = oldParts[0];
            String oldMethodName = oldParts[1];
            
            // Parse new method signature  
            String[] newParts = parseMethodSignature(newMethod);
            String newMethodName = newParts[1];
            
            // Find all method calls matching old signature
            List<CtInvocation<?>> invocations = findMethodCalls(model, oldClassName, oldMethodName);
            
            System.out.println("Found " + invocations.size() + " calls to " + oldMethodName);
            
            // Transform each invocation
            for (CtInvocation<?> invocation : invocations) {
                System.out.println("  Transforming: " + invocation);
                CtExecutableReference<?> execRef = invocation.getExecutable();
                execRef.setSimpleName(newMethodName);
                
                // Warn about potential type issues
                System.out.println("    WARNING: Return type may have changed (check for type mismatches)");
            }
        }
    }
    
    private static void detectRemovedClasses(CtModel model) {
        System.out.println("\n=== Detecting usage of removed classes ===");
        
        for (Map.Entry<String, ReplacementInfo> entry : CLASS_REPLACEMENTS.entrySet()) {
            String removedClass = entry.getKey();
            ReplacementInfo replacement = entry.getValue();
            
            // Find references to removed class
            List<CtTypeReference<?>> references = findTypeReferences(model, removedClass);
            List<CtInvocation<?>> constructors = findConstructorCalls(model, removedClass);
            
            if (!references.isEmpty() || !constructors.isEmpty()) {
                System.out.println("\n" + removedClass + ":");
                System.out.println("  " + replacement.description);
                
                if (!references.isEmpty()) {
                    System.out.println("  Found " + references.size() + " type references");
                    for (CtTypeReference<?> ref : references) {
                        if (ref.getPosition() != null && ref.getPosition().getFile() != null) {
                            System.out.println("    - At: " + ref.getPosition().getFile().getName() + 
                                             ":" + ref.getPosition().getLine());
                        }
                    }
                }
                
                if (!constructors.isEmpty()) {
                    System.out.println("  Found " + constructors.size() + " constructor calls");
                    for (CtInvocation<?> cons : constructors) {
                        if (cons.getPosition() != null && cons.getPosition().getFile() != null) {
                            System.out.println("    - At: " + cons.getPosition().getFile().getName() + 
                                             ":" + cons.getPosition().getLine());
                        }
                    }
                }
                
                if (replacement.replacementPattern != null) {
                    System.out.println("  Suggested replacement: " + replacement.replacementPattern);
                    if (replacement.replacementClass != null) {
                        System.out.println("  May need import: " + replacement.replacementClass);
                    }
                }
            }
        }
    }
    
    private static void detectFieldAccessIssues(CtModel model) {
        System.out.println("\n=== Checking for removed fields ===");
        
        for (Map.Entry<String, String> entry : FIELD_TRANSFORMATIONS.entrySet()) {
            String oldField = entry.getKey();
            String replacement = entry.getValue();
            
            // Parse field access: ClassName.fieldName
            String[] parts = oldField.split("\\.");
            if (parts.length >= 2) {
                String className = String.join(".", java.util.Arrays.copyOfRange(parts, 0, parts.length - 1));
                String fieldName = parts[parts.length - 1];
                
                // We would need to find field accesses, which is more complex
                // For now, just report the pattern
                System.out.println("\nField access pattern to fix:");
                System.out.println("  Old: " + oldField);
                System.out.println("  New: " + replacement);
                System.out.println("  Class: " + className);
                System.out.println("  Field: " + fieldName);
            }
        }
    }
    
    private static String[] parseMethodSignature(String signature) {
        // Format: ClassName.methodName
        int lastDot = signature.lastIndexOf('.');
        String className = signature.substring(0, lastDot);
        String methodWithParams = signature.substring(lastDot + 1);
        
        // Remove parameters
        int parenIndex = methodWithParams.indexOf('(');
        String methodName = parenIndex > 0 ? methodWithParams.substring(0, parenIndex) : methodWithParams;
        
        return new String[]{className, methodName};
    }
    
    private static List<CtInvocation<?>> findMethodCalls(CtModel model, String className, String methodName) {
        return model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null) return false;
                
                // Check method name
                if (!methodName.equals(execRef.getSimpleName())) {
                    return false;
                }
                
                // Check if target type matches or declaring type matches
                CtTypeReference<?> targetType = invocation.getTarget() != null ? 
                    invocation.getTarget().getType() : null;
                
                if (targetType != null && className.equals(targetType.getQualifiedName())) {
                    return true;
                }
                
                // Check declaring type
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                return declaringType != null && className.equals(declaringType.getQualifiedName());
            }
        });
    }
    
    private static List<CtTypeReference<?>> findTypeReferences(CtModel model, String className) {
        return model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> typeRef) {
                String qualifiedName = typeRef.getQualifiedName();
                return qualifiedName != null && qualifiedName.equals(className);
            }
        });
    }
    
    private static List<CtInvocation<?>> findConstructorCalls(CtModel model, String className) {
        return model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> invocation) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (execRef == null) return false;
                
                // Check if it's a constructor
                if (!"<init>".equals(execRef.getSimpleName())) {
                    return false;
                }
                
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                return declaringType != null && className.equals(declaringType.getQualifiedName());
            }
        });
    }
}