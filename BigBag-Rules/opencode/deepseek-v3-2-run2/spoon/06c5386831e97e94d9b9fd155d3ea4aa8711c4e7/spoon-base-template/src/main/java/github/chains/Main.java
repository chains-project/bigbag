package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.Query;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        // Build the model
        launcher.buildModel();
        
        // Apply transformations
        transformScriptResultImports(launcher);
        transformScriptResultConstructorCalls(launcher);
        transformScriptResultMethodCalls(launcher);
        
        // Generate transformed code
        launcher.setSourceOutputDirectory(sourceDirectory);
        launcher.prettyprint();
        
        System.out.println("Transformation completed successfully!");
    }
    
    private static void transformScriptResultImports(Launcher launcher) {
        // Replace old import with new import
        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))
            .stream()
            .filter(ref -> ref.getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult"))
            .forEach(ref -> {
                ref.setSimpleName("ScriptResult");
                ref.setPackage(launcher.getFactory().Package().createReference("org.jenkinsci.test.acceptance.plugins.scriptler"));
            });
    }
    
    private static void transformScriptResultConstructorCalls(Launcher launcher) {
        // Transform constructor calls: new ScriptResult(Object) -> new ScriptResult(String)
        launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))
            .stream()
            .filter(constructorCall -> 
                constructorCall.getType() != null && 
                constructorCall.getType().getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult"))
            .forEach(constructorCall -> {
                // Update the type reference
                CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference("org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult");
                constructorCall.setType(newTypeRef);
                
                // Convert Object parameter to String by calling toString()
                if (constructorCall.getArguments().size() == 1) {
                    CtExpression<?> arg = (CtExpression<?>) constructorCall.getArguments().get(0);
                    CtInvocation<String> toStringCall = launcher.getFactory().createInvocation();
                    toStringCall.setTarget(arg);
                    
                    CtExecutableReference<String> toStringRef = launcher.getFactory().createExecutableReference();
                    toStringRef.setSimpleName("toString");
                    toStringCall.setExecutable(toStringRef);
                    
                    constructorCall.getArguments().set(0, toStringCall);
                }
            });
    }
    
    private static void transformScriptResultMethodCalls(Launcher launcher) {
        // Transform method calls: scriptResult.getJavaScriptResult() -> scriptResult.output(null)
        launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))
            .stream()
            .filter(invocation -> 
                invocation.getTarget() != null &&
                invocation.getExecutable() != null &&
                invocation.getExecutable().getSimpleName().equals("getJavaScriptResult") &&
                invocation.getTarget().getType() != null &&
                invocation.getTarget().getType().getQualifiedName().equals("com.gargoylesoftware.htmlunit.ScriptResult"))
            .forEach(invocation -> {
                // Change method name to "output"
                invocation.getExecutable().setSimpleName("output");
                
                // Add null parameter for Node (since we don't have context for actual Node)
                // In real usage, this should be replaced with appropriate Node parameter
                CtLiteral<Object> nullLiteral = launcher.getFactory().createLiteral();
                nullLiteral.setValue(null);
                invocation.setArguments(java.util.Arrays.asList(nullLiteral));
            });
    }
}