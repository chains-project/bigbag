package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        boolean transformed = false;
        
        // Find all types in the model
        for (CtType<?> type : model.getAllTypes()) {
            // Look for imports of ScriptResult
            for (CtImport ctImport : type.getPosition().getCompilationUnit().getImports()) {
                if (ctImport.getReference() != null) {
                    String importStr = ctImport.getReference().toString();
                    if (importStr.contains("ScriptResult")) {
                        ctImport.delete();
                        transformed = true;
                    }
                }
            }
            
            // Look for constructor calls that might be ScriptResult
            List<CtConstructorCall<?>> constructorCalls = type.getElements(new TypeFilter<>(CtConstructorCall.class));
            for (CtConstructorCall<?> constructorCall : constructorCalls) {
                CtTypeReference<?> typeRef = constructorCall.getType();
                if (typeRef != null && typeRef.getSimpleName().equals("ScriptResult")) {
                    // This looks like a ScriptResult constructor call
                    // Check how it's used
                    CtElement parent = constructorCall.getParent();
                    
                    if (parent instanceof CtInvocation) {
                        CtInvocation<?> invocation = (CtInvocation<?>) parent;
                        if (invocation.getExecutable() != null && 
                            invocation.getExecutable().getSimpleName().equals("getJavaScriptResult")) {
                            // Pattern: new ScriptResult(...).getJavaScriptResult()
                            if (!constructorCall.getArguments().isEmpty()) {
                                invocation.replace(constructorCall.getArguments().get(0));
                                transformed = true;
                            }
                        }
                    } else if (parent instanceof CtVariable) {
                        // Pattern: ScriptResult var = new ScriptResult(...)
                        CtVariable<?> variable = (CtVariable<?>) parent;
                        if (variable.getType().getSimpleName().equals("ScriptResult")) {
                            // Find all uses of this variable with getJavaScriptResult()
                            String varName = variable.getSimpleName();
                            List<CtInvocation<?>> invocations = type.getElements(new TypeFilter<>(CtInvocation.class));
                            for (CtInvocation<?> invocation : invocations) {
                                if (invocation.getExecutable() != null && 
                                    invocation.getExecutable().getSimpleName().equals("getJavaScriptResult") &&
                                    invocation.getTarget() instanceof CtVariableRead) {
                                    CtVariableRead<?> varRead = (CtVariableRead<?>) invocation.getTarget();
                                    if (varRead.getVariable().getSimpleName().equals(varName)) {
                                        // Replace var.getJavaScriptResult() with constructor argument
                                        if (!constructorCall.getArguments().isEmpty()) {
                                            invocation.replace(constructorCall.getArguments().get(0));
                                            transformed = true;
                                        }
                                    }
                                }
                            }
                            // Delete the variable declaration
                            variable.delete();
                            transformed = true;
                        }
                    }
                }
            }
        }
        
        if (transformed) {
            launcher.setSourceOutputDirectory(sourceDir);
            launcher.prettyprint();
            System.out.println("Transformation completed successfully!");
        } else {
            System.out.println("No transformations were applied.");
        }
    }
}