package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java Main <sourceDir> <outputDir>");
            System.out.println("Example: java Main /path/to/src /path/to/output");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Creating transformation for ScriptResult API migration...");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.getEnvironment().setAutoImports(true);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setPreserveLineNumbers(true);
            
            // Add input source
            launcher.addInputResource(sourceDir);
            
            // Build model
            CtModel model = launcher.buildModel();
            
            // Find and transform all ScriptResult usage patterns
            transformScriptResultPatterns(model);
            
            // Set output directory
            launcher.setSourceOutputDirectory(outputDir);
            
            // Pretty-print the transformed code
            launcher.prettyprint();
            
            System.out.println("Transformation complete!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformScriptResultPatterns(CtModel model) {
        // Pattern 1: Remove ScriptResult imports
        removeScriptResultImports(model);
        
        // Pattern 2: Transform variable declarations and usages
        transformScriptResultVariables(model);
        
        // Pattern 3: Transform constructor calls and method invocations
        transformScriptResultExpressions(model);
    }
    
    private static void removeScriptResultImports(CtModel model) {
        // Find all imports
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        List<CtImport> toRemove = new ArrayList<>();
        
        for (CtImport imp : imports) {
            if (imp.getReference() != null) {
                String importStr = imp.getReference().toString();
                if (importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    toRemove.add(imp);
                }
            }
        }
        
        for (CtImport imp : toRemove) {
            imp.delete();
            System.out.println("Removed import: " + imp.getReference());
        }
    }
    
    private static void transformScriptResultVariables(CtModel model) {
        // Find all variable declarations of type ScriptResult
        List<CtVariable<?>> variables = model.getElements(new TypeFilter<>(CtVariable.class) {
            @Override
            public boolean matches(CtVariable<?> element) {
                CtTypeReference<?> varType = element.getType();
                if (varType == null) return false;
                String typeName = varType.getQualifiedName();
                return typeName.equals("com.gargoylesoftware.htmlunit.ScriptResult") ||
                       typeName.equals("ScriptResult");
            }
        });
        
        for (CtVariable<?> variable : variables) {
            // Change variable type to Object
            CtTypeReference<Object> objectType = variable.getFactory().Type().OBJECT;
            variable.setType(objectType);
            System.out.println("Changed variable type from ScriptResult to Object: " + variable.getSimpleName());
            
            // If variable has an initializer that's a ScriptResult constructor, transform it
            CtExpression<?> initializer = variable.getDefaultExpression();
            if (initializer != null) {
                transformScriptResultConstructor(initializer);
            }
        }
    }
    
    private static void transformScriptResultExpressions(CtModel model) {
        // Find all constructor calls to ScriptResult
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                if (element.getType() == null) return false;
                String typeName = element.getType().getQualifiedName();
                return typeName.equals("com.gargoylesoftware.htmlunit.ScriptResult") ||
                       typeName.equals("ScriptResult");
            }
        });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            transformScriptResultConstructor(constructorCall);
        }
        
        // Find all method calls to getJavaScriptResult()
        List<CtInvocation<?>> methodCalls = model.getElements(new TypeFilter<>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                String methodName = element.getExecutable().getSimpleName();
                return methodName.equals("getJavaScriptResult");
            }
        });
        
        for (CtInvocation<?> methodCall : methodCalls) {
            transformGetJavaScriptResultCall(methodCall);
        }
    }
    
    private static void transformScriptResultConstructor(CtExpression<?> expression) {
        if (!(expression instanceof CtConstructorCall)) {
            return;
        }
        
        CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) expression;
        
        // Pattern: new ScriptResult(result) -> result
        if (constructorCall.getArguments().size() == 1) {
            CtExpression<?> argument = constructorCall.getArguments().get(0);
            
            // Check parent context to see how to handle this
            CtElement parent = constructorCall.getParent();
            
            if (parent instanceof CtInvocation) {
                // Pattern: new ScriptResult(result).getJavaScriptResult()
                CtInvocation<?> invocation = (CtInvocation<?>) parent;
                if (invocation.getExecutable().getSimpleName().equals("getJavaScriptResult")) {
                    // Will be handled by transformGetJavaScriptResultCall
                    return;
                }
            }
            
            // Direct replacement
            constructorCall.replace(argument.clone());
            System.out.println("Replaced ScriptResult constructor with its argument");
        }
    }
    
    private static void transformGetJavaScriptResultCall(CtInvocation<?> invocation) {
        // Pattern: something.getJavaScriptResult()
        
        CtExpression<?> target = invocation.getTarget();
        
        if (target instanceof CtConstructorCall) {
            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
            if (constructorCall.getType() != null && 
                (constructorCall.getType().getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult") ||
                 constructorCall.getType().getQualifiedName().equals("ScriptResult"))) {
                
                // Pattern: new ScriptResult(result).getJavaScriptResult() -> result
                if (constructorCall.getArguments().size() == 1) {
                    CtExpression<?> argument = constructorCall.getArguments().get(0);
                    invocation.replace(argument.clone());
                    System.out.println("Transformed new ScriptResult(...).getJavaScriptResult() to direct result");
                }
            }
        } else {
            // Pattern: variable.getJavaScriptResult() -> variable
            // Since we changed variable type to Object, we can just remove the method call
            if (target != null) {
                invocation.replace(target.clone());
                System.out.println("Transformed variable.getJavaScriptResult() to variable");
            }
        }
    }
}