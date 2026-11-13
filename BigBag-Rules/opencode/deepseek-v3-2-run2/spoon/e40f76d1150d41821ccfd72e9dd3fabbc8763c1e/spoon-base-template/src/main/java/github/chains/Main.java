package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import spoon.reflect.reference.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

/**
 * GENERIC TRANSFORMATION TEMPLATE for breaking dependency updates.
 * 
 * This template demonstrates the pattern for fixing:
 * - Removed class: com.gargoylesoftware.htmlunit.ScriptResult
 * - Breaking change: Class removed from dependency
 * 
 * TRANSFORMATION PATTERN:
 * 1. Remove unnecessary wrapper class usage
 * 2. Replace new Wrapper(expr).getMethod() with expr
 * 3. Update variable types from Wrapper to appropriate type
 * 
 * To adapt for other breaking changes:
 * 1. Update REMOVED_CLASS and REMOVED_METHOD constants
 * 2. Adjust transformation logic in processConstructors() and processMethods()
 * 3. Update variable type mapping in updateVariables()
 */
public class Main {
    // CONFIGURATION: Update these for different breaking changes
    private static final String REMOVED_CLASS = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String REMOVED_METHOD = "getJavaScriptResult";
    private static final String REPLACEMENT_TYPE = "java.lang.Object";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.err.println("\nThis transformation fixes: " + REMOVED_CLASS + " removal");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Generic Transformation Rule ===");
        System.out.println("Target: " + REMOVED_CLASS);
        System.out.println("Transformation: Remove wrapper, replace with " + REPLACEMENT_TYPE);
        System.out.println("Source: " + sourceDir);
        System.out.println();
        
        try {
            // Initialize Spoon
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setNoClasspath(false);
            launcher.addInputResource(sourceDir);
            
            // Build model
            System.out.println("Building AST model...");
            CtModel model = launcher.buildModel();
            
            // Process the model
            TransformationResult result = processModel(model, launcher.getFactory());
            
            // Print summary
            printSummary(result);
            
            // Apply transformations (commented out for safety)
            // launcher.setSourceOutputDirectory(sourceDir);
            // launcher.prettyprint();
            
            System.out.println("\nNote: This is a template. Uncomment transformation code to apply changes.");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nDebug tips:");
            System.err.println("1. Ensure source directory exists: " + sourceDir);
            System.err.println("2. Try with --no-classpath flag if dependencies missing");
            System.err.println("3. Check Spoon version compatibility");
            System.exit(1);
        }
    }
    
    static class TransformationResult {
        int importsFound = 0;
        int constructorsFound = 0;
        int methodCallsFound = 0;
        int variablesFound = 0;
        Map<String, String> variableMappings = new HashMap<>();
    }
    
    private static TransformationResult processModel(CtModel model, Factory factory) {
        TransformationResult result = new TransformationResult();
        
        // Find all relevant elements
        for (CtType<?> type : model.getAllTypes()) {
            // Check imports
            for (CtImport imp : type.getImports()) {
                if (imp.getReference().toString().contains(REMOVED_CLASS)) {
                    result.importsFound++;
                    System.out.println("Found import: " + imp.getReference());
                    // type.removeImport(imp); // Uncomment to remove
                }
            }
            
            // Find constructor calls
            for (CtConstructorCall<?> ctor : type.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))) {
                if (isTargetClass(ctor.getType())) {
                    result.constructorsFound++;
                    System.out.println("Found constructor: " + ctor);
                    processConstructor(ctor, factory, result);
                }
            }
            
            // Find method calls
            for (CtInvocation<?> invocation : type.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
                if (REMOVED_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                    result.methodCallsFound++;
                    System.out.println("Found method call: " + invocation);
                    processMethodCall(invocation, factory, result);
                }
            }
            
            // Find variable declarations
            for (CtLocalVariable<?> var : type.getElements(new TypeFilter<CtLocalVariable<?>>(CtLocalVariable.class))) {
                if (isTargetClass(var.getType())) {
                    result.variablesFound++;
                    System.out.println("Found variable: " + var.getSimpleName() + " : " + var.getType());
                    processVariable(var, factory, result);
                }
            }
        }
        
        return result;
    }
    
    private static void processConstructor(CtConstructorCall<?> ctor, Factory factory, TransformationResult result) {
        // Pattern: new ScriptResult(expr).getJavaScriptResult()
        if (ctor.getParent() instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) ctor.getParent();
            if (REMOVED_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                System.out.println("  Pattern: constructor-method chain");
                System.out.println("  Would replace with: " + ctor.getArguments().get(0));
                // invocation.replace(ctor.getArguments().get(0).clone());
            }
        }
        // Pattern: ScriptResult var = new ScriptResult(expr)
        else if (ctor.getParent() instanceof CtLocalVariable) {
            CtLocalVariable<?> var = (CtLocalVariable<?>) ctor.getParent();
            if (ctor.getArguments().size() == 1) {
                result.variableMappings.put(var.getSimpleName(), ctor.getArguments().get(0).toString());
                System.out.println("  Pattern: variable initialization");
            }
        }
    }
    
    private static void processMethodCall(CtInvocation<?> invocation, Factory factory, TransformationResult result) {
        CtExpression<?> target = invocation.getTarget();
        if (target instanceof CtVariableRead) {
            String varName = ((CtVariableRead<?>) target).getVariable().getSimpleName();
            if (result.variableMappings.containsKey(varName)) {
                System.out.println("  Pattern: variable.method() on " + varName);
                System.out.println("  Would replace with: " + result.variableMappings.get(varName));
                // invocation.replace(factory.createCodeSnippetExpression(result.variableMappings.get(varName)));
            }
        }
    }
    
    private static void processVariable(CtLocalVariable<?> var, Factory factory, TransformationResult result) {
        System.out.println("  Would change type from " + var.getType().getSimpleName() + " to " + REPLACEMENT_TYPE);
        // CtTypeReference<?> newType = factory.createCtTypeReference(Object.class);
        // var.setType(newType);
        
        if (var.getDefaultExpression() instanceof CtConstructorCall) {
            CtConstructorCall<?> ctor = (CtConstructorCall<?>) var.getDefaultExpression();
            if (ctor.getArguments().size() == 1) {
                System.out.println("  Would update initialization: " + ctor.getArguments().get(0));
                // var.setDefaultExpression(ctor.getArguments().get(0).clone());
            }
        }
    }
    
    private static boolean isTargetClass(CtTypeReference<?> typeRef) {
        if (typeRef == null) return false;
        return REMOVED_CLASS.equals(typeRef.getQualifiedName()) || 
               "ScriptResult".equals(typeRef.getSimpleName());
    }
    
    private static void printSummary(TransformationResult result) {
        System.out.println("\n=== TRANSFORMATION SUMMARY ===");
        System.out.println("Imports found: " + result.importsFound);
        System.out.println("Constructor calls: " + result.constructorsFound);
        System.out.println("Method calls: " + result.methodCallsFound);
        System.out.println("Variable declarations: " + result.variablesFound);
        System.out.println("Variable mappings: " + result.variableMappings.size());
        System.out.println("\nTotal patterns identified: " + 
            (result.importsFound + result.constructorsFound + result.methodCallsFound + result.variablesFound));
    }
}