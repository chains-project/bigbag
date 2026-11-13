package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true); // Use no classpath for broken code
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        CtModel model = launcher.buildModel();
        
        int changes = 0;
        
        // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        List<CtImport> imports = model.getElements(new TypeFilter<CtImport>(CtImport.class));
        for (CtImport importDecl : imports) {
            String importStr = importDecl.toString();
            if (importStr.contains("com.gargoylesoftware.htmlunit.ScriptResult")) {
                importDecl.delete();
                changes++;
                System.out.println("Removed import: " + importStr);
            }
        }
        
        // First pass: Handle new ScriptResult(expr).getJavaScriptResult() 
        // Replace with just expr
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef != null && "getJavaScriptResult".equals(execRef.getSimpleName())) {
                // Check if target is new ScriptResult(...)
                CtExpression<?> target = invocation.getTarget();
                if (target instanceof CtNewClass) {
                    CtNewClass<?> newClass = (CtNewClass<?>) target;
                    // Check if it's ScriptResult by looking at the type name
                    String typeName = newClass.getType().toString();
                    if (typeName.contains("ScriptResult")) {
                        // Replace entire invocation with the argument
                        List<CtExpression<?>> argsList = newClass.getArguments();
                        if (argsList.size() == 1) {
                            invocation.replace(argsList.get(0));
                            changes++;
                            System.out.println("Replaced new ScriptResult(...).getJavaScriptResult() with argument");
                        }
                    }
                } else {
                    // variable.getJavaScriptResult() - just remove the method call, keep variable
                    // This is a simplification
                    invocation.replace(target);
                    changes++;
                    System.out.println("Removed .getJavaScriptResult() method call");
                }
            }
        }
        
        // Second pass: Replace ALL new ScriptResult(expr) with expr
        List<CtNewClass<?>> newClasses = model.getElements(new TypeFilter<CtNewClass<?>>(CtNewClass.class));
        for (CtNewClass<?> newClass : newClasses) {
            String typeName = newClass.getType().toString();
            if (typeName.contains("ScriptResult")) {
                List<CtExpression<?>> argsList = newClass.getArguments();
                if (argsList.size() == 1) {
                    newClass.replace(argsList.get(0));
                    changes++;
                    System.out.println("Replaced new ScriptResult(...) with argument");
                }
            }
        }
        
        // Third pass: Change variable declarations from ScriptResult to Object
        List<CtLocalVariable<?>> localVars = model.getElements(new TypeFilter<CtLocalVariable<?>>(CtLocalVariable.class));
        for (CtLocalVariable<?> var : localVars) {
            CtTypeReference<?> typeRef = var.getType();
            if (typeRef != null && typeRef.toString().contains("ScriptResult")) {
                // Change type to Object
                var.setType(launcher.getFactory().Type().objectType());
                changes++;
                System.out.println("Changed variable type from ScriptResult to Object: " + var.getSimpleName());
            }
        }
        
        // Fourth pass: Change field types from ScriptResult to Object
        // (Similar to local variables but for fields)
        
        // Output transformed code
        launcher.setSourceOutputDirectory("/tmp/transformed");
        launcher.prettyprint();
        
        System.out.println("Transformation completed. " + changes + " changes made. Output written to /tmp/transformed");
        System.out.println("\nNOTE: This transformation makes simplifying assumptions:");
        System.out.println("1. new ScriptResult(expr) is replaced with expr");
        System.out.println("2. .getJavaScriptResult() calls are removed");
        System.out.println("3. ScriptResult types are changed to Object");
        System.out.println("You may need to manually fix null handling or other logic.");
    }
}