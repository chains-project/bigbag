package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8); // Set Java 8 compliance
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Find and remove ScriptResult imports
            removeScriptResultImports(model);
            
            // Replace ScriptResult constructor calls
            replaceScriptResultConstructorCalls(model);
            
            // Save the transformed code
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void removeScriptResultImports(CtModel model) {
        // Find all compilation units
        List<CtCompilationUnit> compilationUnits = model.getElements(new TypeFilter<CtCompilationUnit>(CtCompilationUnit.class));
        
        int removedCount = 0;
        for (CtCompilationUnit cu : compilationUnits) {
            List<CtImport> importsToRemove = new ArrayList<>();
            
            // Find ScriptResult imports
            for (CtImport imp : cu.getImports()) {
                try {
                    String importStr = imp.toString();
                    if (importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        importsToRemove.add(imp);
                    }
                } catch (Exception e) {
                    // Skip if we can't parse the import
                }
            }
            
            // Remove the imports by clearing them from the list
            cu.getImports().removeAll(importsToRemove);
            removedCount += importsToRemove.size();
        }
        
        System.out.println("Removed " + removedCount + " ScriptResult imports");
    }
    
    private static void replaceScriptResultConstructorCalls(CtModel model) {
        // Find all constructor calls to ScriptResult
        List<CtConstructorCall<?>> constructorCalls = model.getElements(new TypeFilter<CtConstructorCall<?>>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall<?> element) {
                CtTypeReference<?> typeRef = element.getType();
                if (typeRef != null) {
                    String typeName = typeRef.getQualifiedName();
                    return typeName != null && typeName.equals("com.gargoylesoftware.htmlunit.ScriptResult");
                }
                return false;
            }
        });
        
        System.out.println("Found " + constructorCalls.size() + " ScriptResult constructor calls");
        
        // Replace each constructor call with its argument
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            // Get the argument passed to ScriptResult constructor
            if (constructorCall.getArguments().size() == 1) {
                CtExpression<?> arg = constructorCall.getArguments().get(0);
                
                // Check if this is part of a method invocation chain: new ScriptResult(result).getJavaScriptResult()
                CtInvocation<?> parentInvocation = constructorCall.getParent(CtInvocation.class);
                if (parentInvocation != null && 
                    parentInvocation.getTarget() == constructorCall &&
                    "getJavaScriptResult".equals(parentInvocation.getExecutable().getSimpleName())) {
                    
                    // Replace the entire expression: new ScriptResult(result).getJavaScriptResult() -> result
                    parentInvocation.replace(arg);
                    System.out.println("Replaced: " + constructorCall + ".getJavaScriptResult() -> " + arg);
                } else {
                    // Just new ScriptResult(result) -> result
                    constructorCall.replace(arg);
                    System.out.println("Replaced: " + constructorCall + " -> " + arg);
                }
            }
        }
    }
}