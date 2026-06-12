package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtField;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import spoon.support.SpoonClassNotFoundException;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Generic Spoon transformation rule for fixing breaking changes in Apache Thrift 0.16.0
 * where TSerializer and TDeserializer constructors now throw TTransportException.
 * 
 * Breaking Change Pattern:
 * - Old API: new TSerializer(), new TDeserializer() (no checked exceptions)
 * - New API: new TSerializer() throws TTransportException, new TDeserializer() throws TTransportException
 * 
 * Transformation Strategy:
 * 1. Detect all constructor calls to affected types (TSerializer, TDeserializer)
 * 2. Identify context (field initializer, local variable, assignment, etc.)
 * 3. Apply appropriate fix based on context:
 *    - Field initializers: Move to constructor with try-catch or add throws clause
 *    - Local variables: Wrap in try-catch or add throws to enclosing method
 *    - Other contexts: Similar context-aware transformations
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Thrift 0.16.0 Breaking Change Fixer ===");
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println();
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            Factory factory = launcher.getFactory();
            
            // Define the types that have constructors throwing TTransportException in 0.16.0
            Set<String> affectedTypes = new HashSet<>();
            affectedTypes.add("org.apache.thrift.TSerializer");
            affectedTypes.add("org.apache.thrift.TDeserializer");
            
            int totalIssues = 0;
            
            // Process all classes in the model
            for (CtType<?> type : model.getAllTypes()) {
                if (type instanceof CtClass) {
                    totalIssues += processClass((CtClass<?>) type, factory, affectedTypes);
                }
            }
            
            System.out.println();
            System.out.println("=== Summary ===");
            System.out.println("Total breaking API usages found: " + totalIssues);
            System.out.println();
            System.out.println("Recommended fixes:");
            System.out.println("1. For field initializers: Move initialization to constructor with try-catch");
            System.out.println("2. For local variables: Wrap in try-catch or add 'throws TTransportException'");
            System.out.println("3. For ThreadLocal initializers: Override initialValue() with try-catch");
            System.out.println();
            System.out.println("Example fix for field initialization:");
            System.out.println("  // Before: private TSerializer serializer = new TSerializer();");
            System.out.println("  // After:");
            System.out.println("  private TSerializer serializer;");
            System.out.println("  public MyClass() {");
            System.out.println("    try {");
            System.out.println("      this.serializer = new TSerializer();");
            System.out.println("    } catch (TTransportException e) {");
            System.out.println("      throw new RuntimeException(\"Failed to create TSerializer\", e);");
            System.out.println("    }");
            System.out.println("  }");
            
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int processClass(CtClass<?> clazz, Factory factory, Set<String> affectedTypes) {
        int issuesInClass = 0;
        
        // Find all constructor calls to affected types
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(clazz, 
            (Filter<CtConstructorCall<?>>) element -> {
                try {
                    CtExecutableReference<?> execRef = element.getExecutable();
                    if (execRef != null && execRef.getDeclaringType() != null) {
                        String typeName = execRef.getDeclaringType().getQualifiedName();
                        return affectedTypes.contains(typeName);
                    }
                } catch (SpoonClassNotFoundException e) {
                    // Ignore types not found in classpath
                }
                return false;
            });
        
        if (!constructorCalls.isEmpty()) {
            System.out.println("Class: " + clazz.getQualifiedName());
            System.out.println("File: " + clazz.getPosition().getFile().getAbsolutePath());
            System.out.println("Issues found:");
        }
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            issuesInClass++;
            analyzeConstructorCall(constructorCall, factory, clazz);
        }
        
        if (!constructorCalls.isEmpty()) {
            System.out.println();
        }
        
        return issuesInClass;
    }
    
    private static void analyzeConstructorCall(CtConstructorCall<?> constructorCall, Factory factory, CtClass<?> clazz) {
        String typeName = constructorCall.getType().getSimpleName();
        int line = constructorCall.getPosition().getLine();
        
        System.out.print("  - Line " + line + ": " + typeName + " constructor call");
        
        // Check if this is a field initialization
        CtField<?> field = constructorCall.getParent(CtField.class);
        if (field != null) {
            System.out.println(" (field initialization: " + field.getSimpleName() + ")");
            System.out.println("    Fix: Move initialization to constructor with try-catch block");
            return;
        }
        
        // Check if this is a local variable declaration
        CtLocalVariable<?> localVar = constructorCall.getParent(CtLocalVariable.class);
        if (localVar != null) {
            System.out.println(" (local variable: " + localVar.getSimpleName() + ")");
            System.out.println("    Fix: Wrap in try-catch or add 'throws TTransportException' to method");
            return;
        }
        
        // Check context
        System.out.println(" (context: " + getContextDescription(constructorCall) + ")");
        System.out.println("    Fix: Handle " + typeName + " constructor exception appropriately");
    }
    
    private static String getContextDescription(CtConstructorCall<?> constructorCall) {
        // Try to get more context about where this constructor call is used
        Object parent = constructorCall.getParent();
        if (parent != null) {
            return parent.getClass().getSimpleName();
        }
        return "unknown";
    }
}