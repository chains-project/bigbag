package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

/**
 * Generic Spoon transformation rule to fix breaking dependency update:
 * com.gargoylesoftware.htmlunit.ScriptResult class was removed.
 * 
 * This transformation handles:
 * 1. Removing imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replacing "new ScriptResult(...)" with just the argument
 * 3. Removing ".getJavaScriptResult()" method calls
 * 4. Changing variable/parameter/field types from ScriptResult to Object
 * 
 * Usage: java Main <source-directory>
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Apply transformations
        boolean changed = applyTransformations(model);
        
        if (changed) {
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformation completed successfully!");
        } else {
            System.out.println("No ScriptResult references found. No changes made.");
        }
    }
    
    private static boolean applyTransformations(CtModel model) {
        boolean changed = false;
        
        // 1. Remove ScriptResult imports
        changed |= removeScriptResultImports(model);
        
        // 2. Fix variable/parameter/field declarations with ScriptResult type
        changed |= fixScriptResultTypeReferences(model);
        
        // 3. Replace ScriptResult constructor calls
        changed |= fixScriptResultConstructorCalls(model);
        
        // 4. Remove getJavaScriptResult() method calls
        changed |= fixGetJavaScriptResultCalls(model);
        
        return changed;
    }
    
    private static boolean removeScriptResultImports(CtModel model) {
        boolean changed = false;
        List<CtImport> imports = model.getElements(new TypeFilter<>(CtImport.class));
        for (CtImport imp : imports) {
            if (imp.getReference() != null) {
                String importStr = imp.getReference().toString();
                if (importStr.equals("com.gargoylesoftware.htmlunit.ScriptResult") || 
                    importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    imp.delete();
                    System.out.println("Removed import: " + importStr);
                    changed = true;
                }
            }
        }
        return changed;
    }
    
    private static boolean fixScriptResultTypeReferences(CtModel model) {
        boolean changed = false;
        
        // Fix variable declarations
        List<CtVariable> variables = model.getElements(new TypeFilter<>(CtVariable.class));
        for (CtVariable variable : variables) {
            CtTypeReference typeRef = variable.getType();
            if (isScriptResultType(typeRef)) {
                CtTypeReference objectType = variable.getFactory().Type().OBJECT;
                variable.setType(objectType);
                System.out.println("Changed variable type from ScriptResult to Object at: " + variable.getPosition());
                changed = true;
            }
        }
        
        // Fix parameter declarations
        List<CtParameter> parameters = model.getElements(new TypeFilter<>(CtParameter.class));
        for (CtParameter parameter : parameters) {
            CtTypeReference typeRef = parameter.getType();
            if (isScriptResultType(typeRef)) {
                CtTypeReference objectType = parameter.getFactory().Type().OBJECT;
                parameter.setType(objectType);
                System.out.println("Changed parameter type from ScriptResult to Object at: " + parameter.getPosition());
                changed = true;
            }
        }
        
        // Fix field declarations
        List<CtField> fields = model.getElements(new TypeFilter<>(CtField.class));
        for (CtField field : fields) {
            CtTypeReference typeRef = field.getType();
            if (isScriptResultType(typeRef)) {
                CtTypeReference objectType = field.getFactory().Type().OBJECT;
                field.setType(objectType);
                System.out.println("Changed field type from ScriptResult to Object at: " + field.getPosition());
                changed = true;
            }
        }
        
        return changed;
    }
    
    private static boolean fixScriptResultConstructorCalls(CtModel model) {
        boolean changed = false;
        List<CtConstructorCall> constructorCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall constructorCall : constructorCalls) {
            CtTypeReference typeRef = constructorCall.getType();
            if (isScriptResultType(typeRef)) {
                // Replace "new ScriptResult(result)" with just "result"
                if (constructorCall.getArguments().size() == 1) {
                    Object arg = constructorCall.getArguments().get(0);
                    constructorCall.replace((CtElement) arg);
                    System.out.println("Replaced ScriptResult constructor call at: " + constructorCall.getPosition());
                    changed = true;
                } else {
                    System.err.println("Warning: ScriptResult constructor with " + constructorCall.getArguments().size() + 
                                     " arguments at " + constructorCall.getPosition() + 
                                     ". Expected 1 argument.");
                }
            }
        }
        return changed;
    }
    
    private static boolean fixGetJavaScriptResultCalls(CtModel model) {
        boolean changed = false;
        List<CtInvocation> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation invocation : invocations) {
            if ("getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                // Replace "something.getJavaScriptResult()" with just "something"
                Object target = invocation.getTarget();
                if (target != null) {
                    invocation.replace((CtElement) target);
                    System.out.println("Removed getJavaScriptResult() call at: " + invocation.getPosition());
                    changed = true;
                }
            }
        }
        return changed;
    }
    
    private static boolean isScriptResultType(CtTypeReference typeRef) {
        if (typeRef == null) {
            return false;
        }
        String qualifiedName = typeRef.getQualifiedName();
        return qualifiedName != null && qualifiedName.equals("com.gargoylesoftware.htmlunit.ScriptResult");
    }
}