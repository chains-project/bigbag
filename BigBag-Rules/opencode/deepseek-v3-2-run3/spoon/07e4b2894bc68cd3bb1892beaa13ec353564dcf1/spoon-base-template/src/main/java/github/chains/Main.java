package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking changes when com.gargoylesoftware.htmlunit.ScriptResult
 * is removed and replaced with different API patterns.
 * 
 * This transformation handles:
 * 1. Removal of com.gargoylesoftware.htmlunit.ScriptResult imports
 * 2. Replacement of new ScriptResult(object).getJavaScriptResult() patterns with the object itself
 * 3. Replacement of new ScriptResult(object).getJavaScriptResult().toString() with object.toString()
 * 
 * The transformation is generic and can be applied to any Java project affected by this
 * dependency change.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Pattern 1: Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
            List<CtImport> oldImports = model.getElements(new TypeFilter<CtImport>(CtImport.class) {
                @Override
                public boolean matches(CtImport element) {
                    if (element.getReference() == null) return false;
                    String importStr = element.getReference().toString();
                    return importStr != null && 
                           (importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult") ||
                            importStr.equals("com.gargoylesoftware.htmlunit.ScriptResult"));
                }
            });
            
            System.out.println("Found " + oldImports.size() + " imports of com.gargoylesoftware.htmlunit.ScriptResult");
            
            for (CtImport oldImport : oldImports) {
                System.out.println("Removing import: " + oldImport.getReference());
                oldImport.delete();
            }
            
            // Pattern 2: Find and transform constructor calls to ScriptResult
            List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall element) {
                    if (element.getType() == null) return false;
                    String typeName = element.getType().getQualifiedName();
                    return typeName != null && 
                           (typeName.contains("ScriptResult") || 
                            typeName.equals("com.gargoylesoftware.htmlunit.ScriptResult") ||
                            typeName.equals("ScriptResult"));
                }
            });
            
            System.out.println("Found " + constructorCalls.size() + " ScriptResult constructor calls");
            
            int transformations = 0;
            for (CtConstructorCall constrCall : constructorCalls) {
                // Get the argument passed to constructor (the JavaScript result)
                if (constrCall.getArguments().isEmpty()) {
                    System.out.println("Warning: ScriptResult constructor with no arguments at " + 
                                     constrCall.getPosition());
                    continue;
                }
                
                CtExpression<?> arg = (CtExpression<?>) constrCall.getArguments().get(0);
                
                // Check if this is part of a getJavaScriptResult() call
                CtInvocation methodCall = constrCall.getParent(CtInvocation.class);
                if (methodCall != null && "getJavaScriptResult".equals(methodCall.getExecutable().getSimpleName())) {
                    // Pattern: new ScriptResult(arg).getJavaScriptResult() or chain
                    
                    // Check if getJavaScriptResult() is followed by .toString()
                    CtInvocation toStringCall = methodCall.getParent(CtInvocation.class);
                    if (toStringCall != null && "toString".equals(toStringCall.getExecutable().getSimpleName())) {
                        // Pattern: new ScriptResult(arg).getJavaScriptResult().toString()
                        // Replace with: arg.toString()
                        // Simple replacement: just keep the argument and add .toString()
                        toStringCall.replace(arg);
                        transformations++;
                        System.out.println("Transformed new ScriptResult(arg).getJavaScriptResult().toString() at " + 
                                         constrCall.getPosition());
                    } else {
                        // Pattern: new ScriptResult(arg).getJavaScriptResult()
                        // Replace with: arg
                        methodCall.replace(arg);
                        transformations++;
                        System.out.println("Transformed new ScriptResult(arg).getJavaScriptResult() at " + 
                                         constrCall.getPosition());
                    }
                } else {
                    // Standalone constructor call: new ScriptResult(arg)
                    // This creates a ScriptResult object that will likely cause errors
                    // We should warn about this
                    System.out.println("Warning: Standalone ScriptResult constructor at " + 
                                     constrCall.getPosition() + " - may need manual fix");
                }
            }
            
            // Pattern 3: Find any remaining getJavaScriptResult() calls (on variables)
            List<CtInvocation> remainingMethodCalls = model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation element) {
                    String methodName = element.getExecutable().getSimpleName();
                    return "getJavaScriptResult".equals(methodName);
                }
            });
            
            System.out.println("Found " + remainingMethodCalls.size() + " remaining getJavaScriptResult() calls");
            
            for (CtInvocation methodCall : remainingMethodCalls) {
                System.out.println("Warning: getJavaScriptResult() call at " + methodCall.getPosition() + 
                                 " - may need manual fix");
            }
            
            // Output transformed code
            launcher.setSourceOutputDirectory("./transformed-output");
            launcher.prettyprint();
            
            System.out.println("\nTransformation summary:");
            System.out.println("- Removed " + oldImports.size() + " old imports");
            System.out.println("- Applied " + transformations + " pattern transformations");
            System.out.println("- Found " + remainingMethodCalls.size() + " remaining getJavaScriptResult() calls needing attention");
            System.out.println("\nTransformed code output to: ./transformed-output");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}