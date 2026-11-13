package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic Spoon transformation for fixing breaking changes in acceptance-test-harness.
 * 
 * Breaking Change: com.gargoylesoftware.htmlunit.ScriptResult was removed.
 * Old pattern: new ScriptResult(expression) and scriptResult.getJavaScriptResult()
 * New pattern: Use raw expression directly (ScriptResult wrapper removed)
 * 
 * This transformation is applicable to any Maven project affected by this breaking change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            launcher.buildModel();
            
            // Phase 1: Remove ScriptResult constructor calls
            transformScriptResultConstructors(launcher);
            
            // Phase 2: Remove getJavaScriptResult() method calls
            transformGetJavaScriptResultCalls(launcher);
            
            // Phase 3: Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
            removeScriptResultImports(launcher);
            
            // Output transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Transform all constructor calls to com.gargoylesoftware.htmlunit.ScriptResult.
     * Pattern: new ScriptResult(expression) -> expression
     */
    private static void transformScriptResultConstructors(Launcher launcher) {
        List<CtConstructorCall<?>> scriptResultCalls = Query.getElements(
            launcher.getFactory(),
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    if (constructorCall.getExecutable() != null && 
                        constructorCall.getExecutable().getDeclaringType() != null) {
                        String typeName = constructorCall.getExecutable().getDeclaringType().getQualifiedName();
                        // Match both fully qualified and simple name (if imported)
                        return "com.gargoylesoftware.htmlunit.ScriptResult".equals(typeName);
                    }
                    return false;
                }
            }
        );
        
        System.out.println("Found " + scriptResultCalls.size() + " ScriptResult constructor calls");
        
        for (CtConstructorCall<?> constructorCall : scriptResultCalls) {
            List<CtExpression<?>> arguments = constructorCall.getArguments();
            if (arguments != null && arguments.size() == 1) {
                CtExpression<?> arg = arguments.get(0);
                constructorCall.replace(arg);
                System.out.println("Transformed: Replaced 'new ScriptResult(expr)' with 'expr'");
            }
        }
    }
    
    /**
     * Transform all getJavaScriptResult() method calls on ScriptResult objects.
     * Pattern: scriptResult.getJavaScriptResult() -> scriptResult
     */
    private static void transformGetJavaScriptResultCalls(Launcher launcher) {
        List<CtInvocation<?>> getJavaScriptResultCalls = Query.getElements(
            launcher.getFactory(),
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    if (invocation.getExecutable() != null) {
                        String methodName = invocation.getExecutable().getSimpleName();
                        if ("getJavaScriptResult".equals(methodName)) {
                            // Check if target type is ScriptResult
                            CtExpression<?> target = invocation.getTarget();
                            if (target != null && target.getType() != null) {
                                String typeName = target.getType().getQualifiedName();
                                return "com.gargoylesoftware.htmlunit.ScriptResult".equals(typeName);
                            }
                            // Also check if it's a ScriptResult constructor call result
                            if (target instanceof CtConstructorCall) {
                                CtConstructorCall<?> ctorCall = (CtConstructorCall<?>) target;
                                if (ctorCall.getExecutable() != null && 
                                    ctorCall.getExecutable().getDeclaringType() != null) {
                                    String ctorTypeName = ctorCall.getExecutable().getDeclaringType().getQualifiedName();
                                    return "com.gargoylesoftware.htmlunit.ScriptResult".equals(ctorTypeName);
                                }
                            }
                        }
                    }
                    return false;
                }
            }
        );
        
        System.out.println("Found " + getJavaScriptResultCalls.size() + " getJavaScriptResult() calls");
        
        for (CtInvocation<?> invocation : getJavaScriptResultCalls) {
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                invocation.replace(target);
                System.out.println("Transformed: Replaced 'scriptResult.getJavaScriptResult()' with 'scriptResult'");
            }
        }
    }
    
    /**
     * Remove imports of com.gargoylesoftware.htmlunit.ScriptResult.
     */
    private static void removeScriptResultImports(Launcher launcher) {
        List<CtCompilationUnit> compilationUnits = Query.getElements(
            launcher.getFactory(),
            new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class) {}
        );
        
        int removedImports = 0;
        for (CtCompilationUnit cu : compilationUnits) {
            List<CtImport> imports = cu.getImports();
            List<CtImport> importsToRemove = new ArrayList<>();
            
            for (CtImport imp : imports) {
                if (imp.getReference() != null) {
                    String importStr = imp.getReference().toString();
                    if (importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        importsToRemove.add(imp);
                    }
                }
            }
            
            // Remove imports by filtering
            if (!importsToRemove.isEmpty()) {
                imports.removeAll(importsToRemove);
                cu.setImports(imports);
                removedImports += importsToRemove.size();
            }
        }
        
        System.out.println("Removed " + removedImports + " ScriptResult imports");
    }
}