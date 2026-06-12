package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import java.util.List;

/**
 * Generic Spoon transformation rule for fixing breaking changes in acceptance-test-harness
 * where com.gargoylesoftware.htmlunit.ScriptResult was removed and PageObject.executeScript()
 * now returns Object directly instead of requiring ScriptResult wrapping.
 * 
 * Transformation rules:
 * 1. Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replace new ScriptResult(result) with result
 * 3. Replace scriptResult.getJavaScriptResult() with scriptResult
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(sourceDir);
        
        CtModel model = launcher.buildModel();
        
        // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        removeHtmlUnitScriptResultImports(model);
        
        // Replace ScriptResult constructor calls with direct object usage
        replaceScriptResultConstructorCalls(model);
        
        // Replace getJavaScriptResult() calls with direct variable access
        replaceGetJavaScriptResultCalls(model);
        
        // Generate transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
        
        System.out.println("Transformation complete! Applied to: " + sourceDir);
    }
    
    private static void removeHtmlUnitScriptResultImports(CtModel model) {
        List<CtImport> imports = Query.getElements(model.getRootPackage(), new TypeFilter<CtImport>(CtImport.class) {
            @Override
            public boolean matches(CtImport importDecl) {
                String importStr = importDecl.toString();
                return importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult");
            }
        });
        
        for (CtImport importDecl : imports) {
            importDecl.delete();
            System.out.println("Removed import: " + importDecl);
        }
    }
    
    private static void replaceScriptResultConstructorCalls(CtModel model) {
        List<CtConstructorCall<?>> constructorCalls = Query.getElements(model.getRootPackage(), 
            new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall<?> constructorCall) {
                    CtTypeReference<?> typeRef = constructorCall.getType();
                    return typeRef != null && 
                           "com.gargoylesoftware.htmlunit.ScriptResult".equals(typeRef.getQualifiedName());
                }
            });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            List<CtExpression<?>> arguments = constructorCall.getArguments();
            if (!arguments.isEmpty()) {
                CtExpression<?> arg = arguments.get(0);
                constructorCall.replace(arg);
                System.out.println("Replaced ScriptResult constructor call: " + constructorCall.getPosition());
            }
        }
    }
    
    private static void replaceGetJavaScriptResultCalls(CtModel model) {
        List<CtInvocation<?>> methodCalls = Query.getElements(model.getRootPackage(), 
            new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation<?> invocation) {
                    return "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName());
                }
            });
        
        for (CtInvocation<?> methodCall : methodCalls) {
            CtExpression<?> target = methodCall.getTarget();
            if (target != null) {
                methodCall.replace(target);
                System.out.println("Replaced getJavaScriptResult() call: " + methodCall.getPosition());
            }
        }
    }
}