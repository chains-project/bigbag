package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.*;
import spoon.reflect.code.*;
import spoon.support.SpoonClassNotFoundException;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        try {
            CtModel model = launcher.buildModel();
            
            // Apply transformations in the correct order
            applyTransformations(model);
            
            // Apply all transformations
            launcher.prettyprint();
            
            System.out.println("Transformation completed successfully");
            
        } catch (SpoonClassNotFoundException e) {
            System.err.println("Warning: Some classes not found on classpath, continuing anyway...");
            applyTransformations(launcher.getModel());
            launcher.prettyprint();
            System.out.println("Transformation completed (with warnings)");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void applyTransformations(CtModel model) {
        // First collect all transformations to apply
        List<Transformation> transformations = new ArrayList<>();
        
        // Find and record imports to remove
        for (CtPackage pkg : model.getAllPackages()) {
            for (CtType<?> type : pkg.getTypes()) {
                for (CtImport imp : type.getPosition().getCompilationUnit().getImports()) {
                    if (imp.getReference() instanceof CtTypeReference) {
                        CtTypeReference<?> typeRef = (CtTypeReference<?>) imp.getReference();
                        if (typeRef.getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                            transformations.add(new ImportRemoval(imp));
                        }
                    }
                }
            }
        }
        
        // Find constructor calls to replace
        for (CtConstructorCall<?> call : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            CtTypeReference<?> typeRef = call.getType();
            if (typeRef != null && typeRef.getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                if (call.getArguments().size() == 1) {
                    transformations.add(new ConstructorCallReplacement(call));
                }
            }
        }
        
        // Find method calls to replace
        for (CtInvocation<?> inv : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> execRef = inv.getExecutable();
            if (execRef != null && execRef.getSimpleName().equals("getJavaScriptResult")) {
                // Check if the target type is ScriptResult
                CtExpression<?> target = inv.getTarget();
                if (target != null) {
                    CtTypeReference<?> targetType = target.getType();
                    if (targetType != null && targetType.getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        transformations.add(new MethodCallReplacement(inv));
                    }
                }
            }
        }
        
        // Find variable declarations to change type
        for (CtLocalVariable<?> var : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            CtTypeReference<?> typeRef = var.getType();
            if (typeRef != null && typeRef.getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                transformations.add(new VariableTypeChange(var));
            }
        }
        
        // Apply transformations in safe order
        // 1. Remove imports first
        for (Transformation t : transformations) {
            if (t instanceof ImportRemoval) {
                t.apply();
            }
        }
        
        // 2. Apply other transformations
        for (Transformation t : transformations) {
            if (!(t instanceof ImportRemoval)) {
                t.apply();
            }
        }
    }
    
    // Transformation interface
    private interface Transformation {
        void apply();
    }
    
    // Import removal transformation
    private static class ImportRemoval implements Transformation {
        private final CtImport imp;
        
        ImportRemoval(CtImport imp) {
            this.imp = imp;
        }
        
        @Override
        public void apply() {
            imp.delete();
            System.out.println("Removed import: " + imp);
        }
    }
    
    // Constructor call replacement transformation
    private static class ConstructorCallReplacement implements Transformation {
        private final CtConstructorCall<?> call;
        
        ConstructorCallReplacement(CtConstructorCall<?> call) {
            this.call = call;
        }
        
        @Override
        public void apply() {
            CtExpression<?> arg = call.getArguments().get(0);
            call.replace(arg);
            System.out.println("Replaced constructor call with argument: " + call.getPosition());
        }
    }
    
    // Method call replacement transformation
    private static class MethodCallReplacement implements Transformation {
        private final CtInvocation<?> invocation;
        
        MethodCallReplacement(CtInvocation<?> invocation) {
            this.invocation = invocation;
        }
        
        @Override
        public void apply() {
            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                // Create a copy of the target expression to avoid circular reference
                CtExpression<?> targetCopy = target.clone();
                invocation.replace(targetCopy);
                System.out.println("Replaced getJavaScriptResult() call with target: " + invocation.getPosition());
            }
        }
    }
    
    // Variable type change transformation
    private static class VariableTypeChange implements Transformation {
        private final CtLocalVariable<?> variable;
        
        VariableTypeChange(CtLocalVariable<?> variable) {
            this.variable = variable;
        }
        
        @Override
        public void apply() {
            CtTypeReference<Object> objectType = variable.getFactory().Type().OBJECT;
            variable.setType(objectType);
            System.out.println("Changed variable type to Object: " + variable.getSimpleName() + " at " + variable.getPosition());
        }
    }
}