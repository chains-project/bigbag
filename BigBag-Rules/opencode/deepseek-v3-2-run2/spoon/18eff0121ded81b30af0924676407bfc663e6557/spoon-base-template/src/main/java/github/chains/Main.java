import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.factory.Factory;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying transformation to: " + sourceDirectory);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        
        Factory factory = launcher.getFactory();
        CtModel model = launcher.buildModel();
        
        // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        removeScriptResultImports(model);
        
        // Replace new ScriptResult(...).getJavaScriptResult() with the argument
        replaceScriptResultConstructorCalls(model);
        
        // Replace ScriptResult variables with Object
        replaceScriptResultVariables(model);
        
        // Save transformed code
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void removeScriptResultImports(CtModel model) {
        List<CtImport> importsToRemove = new ArrayList<>();
        
        for (CtType<?> type : model.getAllTypes()) {
            for (CtImport imp : type.getPosition().getCompilationUnit().getImports()) {
                if (imp.getReference() != null) {
                    String importStr = imp.getReference().toString();
                    if (importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        importsToRemove.add(imp);
                    }
                }
            }
        }
        
        for (CtImport imp : importsToRemove) {
            imp.delete();
        }
        
        System.out.println("Removed " + importsToRemove.size() + " ScriptResult imports");
    }
    
    private static void replaceScriptResultConstructorCalls(CtModel model) {
        List<CtInvocation> invocationsToReplace = new ArrayList<>();
        Factory factory = model.getRootPackage().getFactory();
        
        // Find all method invocations of getJavaScriptResult()
        List<CtInvocation> getJavaScriptResultCalls = Query.getElements(factory, 
            new TypeFilter<CtInvocation>(CtInvocation.class) {
                @Override
                public boolean matches(CtInvocation invocation) {
                    return "getJavaScriptResult".equals(invocation.getExecutable().getSimpleName());
                }
            });
        
        for (CtInvocation invocation : getJavaScriptResultCalls) {
            CtExpression<?> target = invocation.getTarget();
            if (target instanceof CtConstructorCall) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef != null && typeRef.getQualifiedName().contains("ScriptResult")) {
                    // Get the arguments passed to the constructor
                    List<CtExpression<?>> args = constructorCall.getArguments();
                    if (!args.isEmpty()) {
                        // Replace the entire invocation with the first argument
                        invocation.replace(args.get(0));
                        invocationsToReplace.add(invocation);
                    }
                }
            }
        }
        
        System.out.println("Replaced " + invocationsToReplace.size() + " ScriptResult constructor calls with getJavaScriptResult()");
        
        // Also find direct new ScriptResult(...) without getJavaScriptResult()
        List<CtConstructorCall> constructorCalls = Query.getElements(factory,
            new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
                @Override
                public boolean matches(CtConstructorCall constructorCall) {
                    CtTypeReference<?> typeRef = constructorCall.getType();
                    return typeRef != null && typeRef.getQualifiedName().contains("ScriptResult");
                }
            });
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            // Only replace if not already replaced above (i.e., not part of getJavaScriptResult() chain)
            if (constructorCall.getParent(CtInvocation.class) == null) {
                List<CtExpression<?>> args = constructorCall.getArguments();
                if (!args.isEmpty()) {
                    constructorCall.replace(args.get(0));
                }
            }
        }
        
        System.out.println("Replaced " + constructorCalls.size() + " standalone ScriptResult constructor calls");
    }
    
    private static void replaceScriptResultVariables(CtModel model) {
        Factory factory = model.getRootPackage().getFactory();
        
        // Find local variables of type ScriptResult and change their type to Object
        List<CtLocalVariable> scriptResultVars = Query.getElements(factory,
            new TypeFilter<CtLocalVariable>(CtLocalVariable.class) {
                @Override
                public boolean matches(CtLocalVariable variable) {
                    CtTypeReference<?> typeRef = variable.getType();
                    return typeRef != null && typeRef.getQualifiedName().contains("ScriptResult");
                }
            });
        
        for (CtLocalVariable var : scriptResultVars) {
            CtTypeReference<Object> objectType = factory.Type().OBJECT;
            var.setType(objectType);
        }
        
        System.out.println("Updated type of " + scriptResultVars.size() + " ScriptResult variables to Object");
        
        // Also update method parameters and return types if needed
        for (CtType<?> type : model.getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                // Check return type
                CtTypeReference<?> returnType = method.getType();
                if (returnType != null && returnType.getQualifiedName().contains("ScriptResult")) {
                    method.setType(factory.Type().OBJECT);
                }
                
                // Check parameters
                for (CtParameter<?> param : method.getParameters()) {
                    CtTypeReference<?> paramType = param.getType();
                    if (paramType != null && paramType.getQualifiedName().contains("ScriptResult")) {
                        param.setType(factory.Type().OBJECT);
                    }
                }
            }
        }
    }
}