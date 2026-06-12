package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;

/**
 * Generic transformation to fix the breaking change where 
 * com.gargoylesoftware.htmlunit.ScriptResult class has been removed.
 * 
 * The transformation handles:
 * 1. Removing imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replacing new ScriptResult(expr) with expr
 * 3. Replacing new ScriptResult(expr).getJavaScriptResult() with expr
 * 
 * This transformation can be applied to any Java project affected by this
 * breaking dependency update.
 */
public class ScriptResultTransformation {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.ScriptResultTransformation <source-directory>");
            System.err.println("Applies transformation to fix ScriptResult breaking change.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying ScriptResult transformation to: " + sourceDir);
        
        try {
            applyTransformation(sourceDir);
            System.out.println("Transformation complete.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static void applyTransformation(String sourceDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);  // Don't try to resolve dependencies
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setLevel("OFF");  // Reduce logging
        
        CtModel model = launcher.buildModel();
        
        // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        removeScriptResultImports(model);
        
        // Replace new ScriptResult(expr) and new ScriptResult(expr).getJavaScriptResult() with expr
        replaceScriptResultCalls(model);
        
        // Save transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }
    
    private static void removeScriptResultImports(CtModel model) {
        List<CtImport> importsToRemove = new ArrayList<>();
        
        for (CtType<?> type : model.getAllTypes()) {
            if (type.getPosition() != null && type.getPosition().getCompilationUnit() != null) {
                for (CtImport imp : type.getPosition().getCompilationUnit().getImports()) {
                    if (imp.getReference() != null) {
                        String importStr = imp.getReference().toString();
                        // Match com.gargoylesoftware.htmlunit.ScriptResult or any ScriptResult from gargoylesoftware
                        if (importStr.equals("com.gargoylesoftware.htmlunit.ScriptResult") ||
                            (importStr.endsWith(".ScriptResult") && importStr.contains("gargoylesoftware"))) {
                            importsToRemove.add(imp);
                        }
                    }
                }
            }
        }
        
        for (CtImport imp : importsToRemove) {
            imp.delete();
        }
        
        System.out.println("Removed " + importsToRemove.size() + " ScriptResult imports");
    }
    
    private static void replaceScriptResultCalls(CtModel model) {
        // Find all constructor calls to ScriptResult
        List<CtConstructorCall<?>> scriptResultCalls = new ArrayList<>();
        for (CtConstructorCall<?> constructorCall : model.getRootPackage().getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class))) {
            CtTypeReference<?> typeRef = constructorCall.getType();
            if (typeRef != null) {
                String typeName = typeRef.getSimpleName();
                if (typeName.equals("ScriptResult")) {
                    // Check qualified name if available
                    String qualifiedName = typeRef.getQualifiedName();
                    if (qualifiedName == null || qualifiedName.isEmpty() ||
                        qualifiedName.equals("com.gargoylesoftware.htmlunit.ScriptResult") ||
                        qualifiedName.contains("gargoylesoftware")) {
                        scriptResultCalls.add(constructorCall);
                    }
                }
            }
        }
        
        System.out.println("Found " + scriptResultCalls.size() + " ScriptResult constructor calls");
        
        // Process in reverse order to handle nested cases correctly
        for (int i = scriptResultCalls.size() - 1; i >= 0; i--) {
            CtConstructorCall<?> constructorCall = scriptResultCalls.get(i);
            processScriptResultCall(constructorCall);
        }
    }
    
    private static void processScriptResultCall(CtConstructorCall<?> constructorCall) {
        if (constructorCall.getArguments().isEmpty()) {
            return;  // No arguments, can't replace
        }
        
        CtExpression<?> arg = constructorCall.getArguments().get(0);
        
        // Check if this is part of a method invocation: new ScriptResult(...).getJavaScriptResult()
        CtExpression<?> parent = constructorCall.getParent(CtExpression.class);
        
        if (parent instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) parent;
            if (invocation.getTarget() == constructorCall) {
                String methodName = invocation.getExecutable().getSimpleName();
                if ("getJavaScriptResult".equals(methodName)) {
                    // Replace the entire invocation with the argument
                    invocation.replace(arg);
                    System.out.println("Replaced new ScriptResult(...).getJavaScriptResult() with argument");
                    return;
                }
            }
        }
        
        // Not a method invocation, just replace the constructor call with the argument
        constructorCall.replace(arg);
        System.out.println("Replaced new ScriptResult(...) with argument");
    }
}