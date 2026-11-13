package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.*;
import java.util.*;

/**
 * Generic transformation rule for fixing breaking changes where
 * com.gargoylesoftware.htmlunit.ScriptResult was removed and
 * executeScript() now returns Object directly instead of wrapping in ScriptResult.
 * 
 * TRANSFORMATION PATTERN:
 * 
 * Breaking Change Description:
 * - Class removed: com.gargoylesoftware.htmlunit.ScriptResult
 * - Method signature change: executeScript() returns Object directly instead of ScriptResult wrapper
 * 
 * Old API Pattern:
 * 1. import com.gargoylesoftware.htmlunit.ScriptResult;
 * 2. ScriptResult scriptResult = new ScriptResult(result);
 * 3. return scriptResult.getJavaScriptResult().toString();
 * 4. Object scriptResult = new ScriptResult(result).getJavaScriptResult();
 * 
 * New API Pattern:
 * 1. (import removed)
 * 2. return result.toString();
 * 3. Object result = pageObject.executeScript(...);
 * 
 * Generic Transformation Rules:
 * 1. Remove all imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replace "new ScriptResult(expr)" with "expr"
 * 3. Replace "expr.getJavaScriptResult()" with "expr"
 * 4. Replace type references "ScriptResult" with "Object"
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transformation: Fixes removal of com.gargoylesoftware.htmlunit.ScriptResult");
            System.err.println("Patterns transformed:");
            System.err.println("  1. Removes ScriptResult imports");
            System.err.println("  2. Replaces new ScriptResult(expr) with expr");
            System.err.println("  3. Replaces expr.getJavaScriptResult() with expr");
            System.err.println("  4. Replaces ScriptResult type references with Object");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Applying transformation for ScriptResult removal...");
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Apply transformation for ScriptResult removal
            boolean changed = transformScriptResultUsage(model);
            
            if (changed) {
                // Write transformed code back
                launcher.setSourceOutputDirectory(sourceDir);
                launcher.prettyprint();
                System.out.println("Transformation completed successfully - files updated");
            } else {
                System.out.println("No ScriptResult usage found - no changes made");
            }
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transforms code that uses ScriptResult class
     * @return true if any changes were made
     */
    private static boolean transformScriptResultUsage(CtModel model) {
        boolean changed = false;
        
        // 1. Remove ScriptResult imports by processing each file
        for (CtCompilationUnit cu : model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class))) {
            List<CtImport> imports = cu.getImports();
            List<CtImport> newImports = new ArrayList<>();
            
            for (CtImport imp : imports) {
                if (imp.getReference() instanceof CtTypeReference) {
                    CtTypeReference typeRef = (CtTypeReference) imp.getReference();
                    if ("com.gargoylesoftware.htmlunit.ScriptResult".equals(typeRef.getQualifiedName())) {
                        changed = true;
                        continue; // Skip this import
                    }
                }
                newImports.add(imp);
            }
            
            if (changed) {
                // Replace imports
                imports.clear();
                imports.addAll(newImports);
            }
        }
        
        // 2. Replace constructor calls: new ScriptResult(expr) -> expr
        for (CtConstructorCall consCall : model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class))) {
            CtTypeReference typeRef = consCall.getType();
            if (typeRef != null && "com.gargoylesoftware.htmlunit.ScriptResult".equals(typeRef.getQualifiedName())) {
                if (consCall.getArguments().size() == 1) {
                    CtExpression<?> arg = consCall.getArguments().get(0);
                    consCall.replace(arg);
                    changed = true;
                    System.out.println("Replaced: new ScriptResult(...) -> expression");
                }
            }
        }
        
        // 3. Replace method calls: expr.getJavaScriptResult() -> expr
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class))) {
            CtExecutableReference execRef = invocation.getExecutable();
            if (execRef != null && "getJavaScriptResult".equals(execRef.getSimpleName())) {
                if (execRef.getDeclaringType() != null && 
                    "com.gargoylesoftware.htmlunit.ScriptResult".equals(execRef.getDeclaringType().getQualifiedName())) {
                    CtExpression<?> target = invocation.getTarget();
                    if (target != null) {
                        invocation.replace(target);
                        changed = true;
                        System.out.println("Replaced: .getJavaScriptResult() -> target expression");
                    }
                }
            }
        }
        
        // 4. Replace type references: ScriptResult -> Object
        // Note: This handles variable declarations, parameter types, return types, etc.
        for (CtTypeReference<?> typeRef : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            if ("com.gargoylesoftware.htmlunit.ScriptResult".equals(typeRef.getQualifiedName())) {
                // Skip if this is part of a constructor call (handled above)
                if (typeRef.getParent() instanceof CtConstructorCall) {
                    continue;
                }
                CtTypeReference<?> objectType = typeRef.getFactory().Type().OBJECT;
                typeRef.replace(objectType);
                changed = true;
                System.out.println("Replaced type reference: ScriptResult -> Object");
            }
        }
        
        return changed;
    }
}