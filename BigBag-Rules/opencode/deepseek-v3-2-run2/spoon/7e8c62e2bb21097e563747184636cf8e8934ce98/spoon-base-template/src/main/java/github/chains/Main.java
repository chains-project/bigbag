package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.Query;
import java.util.List;
import java.util.ArrayList;

/**
 * GENERIC Spoon transformation to fix breaking dependency changes.
 * 
 * This transformation fixes the removal of ScriptResult from htmlunit in
 * Jenkins acceptance-test-harness. It's generic and can be adapted for
 * similar breaking changes by modifying the constants below.
 * 
 * Transformation rules:
 * 1. Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
 * 2. Replace new ScriptResult(x) with x
 * 3. Replace x.getJavaScriptResult() with x
 * 4. Replace new ScriptResult(x).getJavaScriptResult() with x
 * 5. Change ScriptResult variable types to Object
 */
public class Main {
    
    // Configuration - change these for different breaking changes
    private static final String OLD_TYPE_FQN = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String OLD_TYPE_SIMPLE_NAME = "ScriptResult";
    private static final String UNWRAP_METHOD_NAME = "getJavaScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            Factory factory = launcher.getFactory();
            CtModel model = launcher.buildModel();
            
            int transformations = 0;
            
            // 1. Remove imports of ScriptResult
            transformations += removeOldImports(model);
            
            // 2. Process all types in the model
            for (CtType<?> type : model.getAllTypes()) {
                transformations += processType(factory, type);
            }
            
            System.out.println("Total transformations applied: " + transformations);
            
            // Write transformed code
            launcher.setSourceOutputDirectory(sourceDir + "-transformed");
            launcher.prettyprint();
            
            System.out.println("Transformation complete. Output written to: " + sourceDir + "-transformed");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int removeOldImports(CtModel model) {
        int count = 0;
        for (CtType<?> type : model.getAllTypes()) {
            if (type.getPosition() == null || type.getPosition().getCompilationUnit() == null) {
                continue;
            }
            List<CtImport> imports = new ArrayList<>(type.getPosition().getCompilationUnit().getImports());
            for (CtImport imp : imports) {
                if (isOldTypeImport(imp)) {
                    imp.delete();
                    count++;
                    System.out.println("Removed import: " + imp.getReference());
                }
            }
        }
        return count;
    }
    
    private static int processType(Factory factory, CtType<?> type) {
        int count = 0;
        
        // Find constructor calls
        List<CtConstructorCall<?>> ctors = Query.getElements(type, new Filter<CtConstructorCall<?>>() {
            @Override public boolean matches(CtConstructorCall<?> element) {
                return isOldTypeConstructor(element);
            }
        });
        
        for (CtConstructorCall<?> ctor : new ArrayList<>(ctors)) {
            count += processConstructorCall(ctor);
        }
        
        // Find method calls
        List<CtInvocation<?>> invocations = Query.getElements(type, new Filter<CtInvocation<?>>() {
            @Override public boolean matches(CtInvocation<?> element) {
                return isUnwrapMethodCall(element);
            }
        });
        
        for (CtInvocation<?> inv : new ArrayList<>(invocations)) {
            count += processMethodCall(inv);
        }
        
        // Change variable types
        List<CtVariable<?>> variables = Query.getElements(type, new Filter<CtVariable<?>>() {
            @Override public boolean matches(CtVariable<?> element) {
                return isOldTypeVariable(element);
            }
        });
        
        for (CtVariable<?> var : new ArrayList<>(variables)) {
            var.setType(factory.Type().OBJECT);
            count++;
            System.out.println("Changed variable type: " + var.getSimpleName());
        }
        
        return count;
    }
    
    private static boolean isOldTypeImport(CtImport imp) {
        try {
            String importStr = imp.toString();
            return importStr.contains(OLD_TYPE_FQN) || 
                   importStr.contains(OLD_TYPE_SIMPLE_NAME);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static boolean isOldTypeConstructor(CtConstructorCall<?> ctor) {
        try {
            CtTypeReference<?> typeRef = ctor.getType();
            if (typeRef != null) {
                String name = typeRef.getSimpleName();
                return OLD_TYPE_SIMPLE_NAME.equals(name);
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
    
    private static int processConstructorCall(CtConstructorCall<?> ctor) {
        if (ctor.getArguments().size() != 1) {
            return 0;
        }
        
        CtExpression<?> arg = ctor.getArguments().get(0);
        
        // Check for chained call: new ScriptResult(x).getJavaScriptResult()
        CtElement parent = ctor.getParent();
        if (parent instanceof CtInvocation) {
            CtInvocation<?> inv = (CtInvocation<?>) parent;
            if (isUnwrapMethodCall(inv) && inv.getTarget() == ctor) {
                inv.replace(arg.clone());
                System.out.println("Replaced chained constructor/method call");
                return 1;
            }
        }
        
        // Simple constructor: new ScriptResult(x) -> x
        ctor.replace(arg.clone());
        System.out.println("Replaced constructor call");
        return 1;
    }
    
    private static boolean isUnwrapMethodCall(CtInvocation<?> inv) {
        try {
            String methodName = inv.getExecutable().getSimpleName();
            return UNWRAP_METHOD_NAME.equals(methodName);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static int processMethodCall(CtInvocation<?> inv) {
        CtExpression<?> target = inv.getTarget();
        if (target != null) {
            // Skip if target is already a constructor call (handled above)
            if (target instanceof CtConstructorCall && isOldTypeConstructor((CtConstructorCall<?>) target)) {
                return 0;
            }
            
            inv.replace(target.clone());
            System.out.println("Replaced method call");
            return 1;
        }
        return 0;
    }
    
    private static boolean isOldTypeVariable(CtVariable<?> var) {
        try {
            CtTypeReference<?> typeRef = var.getType();
            if (typeRef != null) {
                String name = typeRef.getSimpleName();
                return OLD_TYPE_SIMPLE_NAME.equals(name);
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
}