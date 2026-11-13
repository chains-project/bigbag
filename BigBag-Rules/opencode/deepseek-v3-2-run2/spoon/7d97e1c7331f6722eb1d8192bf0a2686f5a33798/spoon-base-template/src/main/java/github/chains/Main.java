package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtFieldAccessImpl;
import spoon.reflect.factory.Factory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * A generic Spoon transformation to replace field accesses to a removed class
 * with literal values. This handles the breaking change where a class containing
 * only static final constants is removed from a dependency.
 * 
 * The transformation is parameterized by:
 * 1. The fully-qualified class name that was removed
 * 2. A mapping from field names to their corresponding literal values
 * 
 * This transformation can be applied to ANY Maven project affected by the
 * same breaking change by simply changing the parameters.
 */
public class Main {
    
    /**
     * Main method that configures and runs the Spoon transformation.
     * 
     * @param args Command line arguments:
     *             args[0]: Path to the project source directory
     *             args[1]: Fully-qualified class name that was removed (e.g., "com.jcabi.aspects.Tv")
     *             args[2+]: Field mappings in format "fieldName=value" (e.g., "TEN=10", "EIGHT=8")
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java -jar transform.jar <sourceDir> <removedClassName> <field1=value1> [field2=value2 ...]");
            System.err.println("Example: java -jar transform.jar /path/to/project/src/main/java com.jcabi.aspects.Tv TEN=10 EIGHT=8");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String removedClassName = args[1];
        String simpleClassName = getSimpleClassName(removedClassName);
        
        // Parse field mappings
        Map<String, String> fieldMappings = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            String[] parts = args[i].split("=", 2);
            if (parts.length == 2) {
                fieldMappings.put(parts[0], parts[1]);
            } else {
                System.err.println("Invalid field mapping: " + args[i] + ". Expected format: fieldName=value");
                System.exit(1);
            }
        }
        
        System.out.println("Transformation configuration:");
        System.out.println("  Source directory: " + sourceDir);
        System.out.println("  Removed class: " + removedClassName);
        System.out.println("  Simple class name: " + simpleClassName);
        System.out.println("  Field mappings: " + fieldMappings);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Count of transformations applied
        int transformationCount = 0;
        List<CtImport> importsToRemove = new ArrayList<>();
        
        // First, find and mark imports of the removed class for removal
        for (CtType<?> type : model.getAllTypes()) {
            for (CtImport imp : type.getPosition().getCompilationUnit().getImports()) {
                if (imp.getReference() != null) {
                    String importStr = imp.getReference().toString();
                    if (importStr.equals(removedClassName) || importStr.equals(simpleClassName)) {
                        importsToRemove.add(imp);
                    }
                }
            }
        }
        
        // Remove imports of the removed class
        for (CtImport imp : importsToRemove) {
            imp.delete();
            System.out.println("Removed import: " + imp.getReference());
        }
        
        // Find all field accesses in the model
        for (CtFieldAccess<?> fieldAccess : model.getElements(new TypeFilter<>(CtFieldAccess.class))) {
            // Check if this is a field access to the removed class
            if (isFieldAccessToRemovedClass(fieldAccess, removedClassName, simpleClassName)) {
                String fieldName = fieldAccess.getVariable().getSimpleName();
                
                // Check if we have a mapping for this field
                if (fieldMappings.containsKey(fieldName)) {
                    String value = fieldMappings.get(fieldName);
                    
                    // Try to parse the value as an integer
                    try {
                        int intValue = Integer.parseInt(value);
                        CtLiteral<Integer> literal = factory.createLiteral(intValue);
                        fieldAccess.replace(literal);
                        System.out.println("Replaced " + removedClassName + "." + fieldName + " with literal: " + intValue);
                        transformationCount++;
                    } catch (NumberFormatException e) {
                        System.err.println("Warning: Cannot parse value '" + value + "' for field " + fieldName + " as integer");
                    }
                } else {
                    System.err.println("Warning: No mapping found for field: " + removedClassName + "." + fieldName);
                }
            }
        }
        
        System.out.println("\nApplied " + transformationCount + " transformations");
        System.out.println("Removed " + importsToRemove.size() + " imports");
        
        // Output the transformed code
        if (transformationCount > 0 || importsToRemove.size() > 0) {
            System.out.println("\nGenerating transformed code...");
            launcher.setSourceOutputDirectory("./transformed-output");
            launcher.prettyprint();
            System.out.println("Transformed code written to ./transformed-output");
        }
    }
    
    /**
     * Check if a field access is to a field of the removed class.
     * 
     * @param fieldAccess The field access to check
     * @param removedClassName The fully-qualified name of the removed class
     * @param simpleClassName The simple name of the removed class (without package)
     * @return true if the field access is to a field of the removed class
     */
    private static boolean isFieldAccessToRemovedClass(CtFieldAccess<?> fieldAccess, String removedClassName, String simpleClassName) {
        CtFieldReference<?> fieldRef = fieldAccess.getVariable();
        if (fieldRef != null) {
            CtTypeReference<?> declaringType = fieldRef.getDeclaringType();
            if (declaringType != null) {
                String typeName = declaringType.getQualifiedName();
                return typeName.equals(removedClassName);
            }
        }
        
        // Also check by looking at the target expression
        if (fieldAccess.getTarget() != null) {
            String targetStr = fieldAccess.getTarget().toString();
            return targetStr.equals(simpleClassName) || targetStr.equals(removedClassName);
        }
        
        return false;
    }
    
    /**
     * Extract the simple class name from a fully-qualified class name.
     * 
     * @param className Fully-qualified class name
     * @return Simple class name without package
     */
    private static String getSimpleClassName(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot >= 0) {
            return className.substring(lastDot + 1);
        }
        return className;
    }
}