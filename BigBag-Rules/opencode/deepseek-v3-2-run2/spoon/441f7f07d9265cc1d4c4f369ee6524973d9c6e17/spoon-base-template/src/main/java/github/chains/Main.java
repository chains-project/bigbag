package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking dependency updates where
 * com.gargoylesoftware.htmlunit.ScriptResult class was removed.
 * 
 * Breaking Change Analysis:
 * - Old API: com.gargoylesoftware.htmlunit.ScriptResult class existed
 * - New API: Class removed entirely, no direct replacement
 * - Transformation: Remove ScriptResult wrapper, work with objects directly
 * 
 * Transformation Patterns:
 * 1. Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replace new ScriptResult(object) with object
 * 3. Replace new ScriptResult(object).getJavaScriptResult() with object
 * 
 * Limitations:
 * - Cannot handle variable declarations of type ScriptResult without type analysis
 * - Cannot determine what to replace variable.getJavaScriptResult() with
 * - May require manual fixes for complex cases
 * 
 * This is a template transformation that can be parameterized with:
 * - oldTypeQualifiedName: Fully qualified name of removed class
 * - oldMethodName: Method name to remove (getJavaScriptResult)
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-directory> <output-directory>");
            System.err.println("Example: java Main /path/to/project /path/to/output");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        CtModel model = launcher.buildModel();
        
        // Parameterizable configuration - these would be command-line arguments in production
        String oldTypeQualifiedName = "com.gargoylesoftware.htmlunit.ScriptResult";
        String oldMethodName = "getJavaScriptResult";
        // Note: In a real system, these would be parameters, not hardcoded
        
        // Step 1: Remove imports of the old class
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null && oldTypeQualifiedName.equals(imp.getReference().toString())) {
                imp.delete();
                System.out.println("Removed import: " + oldTypeQualifiedName);
            }
        }
        
        // Step 2: Process method invocations first to handle patterns like
        // new ScriptResult(obj).getJavaScriptResult()
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> inv : invocations) {
            CtExecutableReference execRef = inv.getExecutable();
            if (execRef != null && oldMethodName.equals(execRef.getSimpleName())) {
                CtExpression<?> target = inv.getTarget();
                
                // Pattern: new ScriptResult(obj).getJavaScriptResult()
                // Should become: obj
                if (target instanceof CtConstructorCall) {
                    CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) target;
                    if (ctorCall.getType() != null && oldTypeQualifiedName.equals(ctorCall.getType().toString())) {
                        List<CtExpression<?>> ctorArgs = ctorCall.getArguments();
                        if (!ctorArgs.isEmpty()) {
                            // Replace entire expression: new ScriptResult(obj).getJavaScriptResult() -> obj
                            inv.replace(ctorArgs.get(0));
                            System.out.println("Replaced pattern: new " + oldTypeQualifiedName + "(...)." + oldMethodName + "() with constructor argument");
                            continue;
                        }
                    }
                }
                
                // Pattern: variable.getJavaScriptResult() 
                // We need to know what's in the variable. Without type analysis, we can't know.
                // For a generic transformation, we'll log a warning but not transform
                System.out.println("Warning: Found " + oldMethodName + "() call on variable. Manual review needed.");
            }
        }
        
        // Step 3: Replace remaining constructor calls
        // These are constructor calls not followed by .getJavaScriptResult()
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : constructorCalls) {
            CtTypeReference typeRef = call.getType();
            if (typeRef != null && oldTypeQualifiedName.equals(typeRef.toString())) {
                // new ScriptResult(obj) -> obj
                List<CtExpression<?>> constructorArgs = call.getArguments();
                if (!constructorArgs.isEmpty()) {
                    call.replace(constructorArgs.get(0));
                    System.out.println("Replaced constructor call: new " + oldTypeQualifiedName + "(...) with argument");
                }
            }
        }
        
        // Pretty print the transformed code
        launcher.prettyprint();
        
        System.out.println("Transformation completed. Output written to: " + outputDir);
        System.out.println("Summary: Removed " + oldTypeQualifiedName + " usage patterns");
    }
}