package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtLiteral;

/**
 * Generic transformation rule to fix Jakarta Servlet API 6.0.0 breaking changes.
 * This fixes deprecated HttpSession methods that were removed in the new API version.
 * Specifically, it handles:
 * - HttpSession.getSessionContext() removal
 * - HttpSession.getValue(String) removal
 * - HttpSession.getValueNames() removal
 * - HttpSession.putValue(String, Object) removal
 */
public class Main {
    
    public static void main(String[] args) {
        // Default values - can be overridden by command-line arguments
        String sourceDirectory = "/workspace/myfaces-tobago";
        String targetDirectory = "/workspace/myfaces-tobago-fixed";
        
        if (args.length >= 1) {
            sourceDirectory = args[0];
        }
        if (args.length >= 2) {
            targetDirectory = args[1];
        }
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDirectory);
        launcher.setSourceOutputDirectory(targetDirectory);
        
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        // Transform deprecated HttpSession methods
        transformHttpSessionDeclarations(model, factory);
        
        // Process the model to apply transformations
        launcher.process();
        
        System.out.println("Transformation completed successfully.");
        System.out.println("Transformed code saved to: " + targetDirectory);
    }
    
    /**
     * Transforms deprecated HttpSession method calls to be compatible with Jakarta Servlet API 6.0.0
     */
    private static void transformHttpSessionDeclarations(CtModel model, Factory factory) {
        // Transform HttpSession.getSessionContext() calls
        model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getTarget() != null && element.getTarget().getType() != null) {
                    // Check if the invocation is on an HttpSession object
                    CtTypeReference<?> targetType = element.getTarget().getType();
                    return targetType.getQualifiedName().equals("jakarta.servlet.http.HttpSession") &&
                           element.getExecutable().getSimpleName().equals("getSessionContext");
                }
                return false;
            }
        }).forEach(invocation -> {
            // Replace with null constant since it's deprecated and removed
            CtLiteral<Object> nullLiteral = factory.Code().createLiteral(null);
            invocation.replace(nullLiteral);
        });
        
        // Transform HttpSession.getValue(String) calls
        model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getTarget() != null && element.getTarget().getType() != null) {
                    // Check if the invocation is on an HttpSession object
                    CtTypeReference<?> targetType = element.getTarget().getType();
                    return targetType.getQualifiedName().equals("jakarta.servlet.http.HttpSession") &&
                           element.getExecutable().getSimpleName().equals("getValue");
                }
                return false;
            }
        }).forEach(invocation -> {
            // Replace with getAttribute() call
            CtExecutableReference<?> getAttributeRef = factory.Executable().createReference(
                "jakarta.servlet.http.HttpSession.getAttribute(java.lang.String)");
            // Convert the argument to proper CtExpression
            CtExpression<?> arg = invocation.getArguments().get(0);
            CtInvocation newInvocation = factory.Code().createInvocation(
                invocation.getTarget(), 
                getAttributeRef, 
                arg
            );
            invocation.replace(newInvocation);
        });
        
        // Transform HttpSession.getValueNames() calls
        model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getTarget() != null && element.getTarget().getType() != null) {
                    // Check if the invocation is on an HttpSession object
                    CtTypeReference<?> targetType = element.getTarget().getType();
                    return targetType.getQualifiedName().equals("jakarta.servlet.http.HttpSession") &&
                           element.getExecutable().getSimpleName().equals("getValueNames");
                }
                return false;
            }
        }).forEach(invocation -> {
            // Replace with getAttributeNames() call
            CtExecutableReference<?> getAttributeNamesRef = factory.Executable().createReference(
                "jakarta.servlet.http.HttpSession.getAttributeNames()");
            CtInvocation newInvocation = factory.Code().createInvocation(
                invocation.getTarget(),
                getAttributeNamesRef
            );
            invocation.replace(newInvocation);
        });
        
        // Transform HttpSession.putValue(String, Object) calls
        model.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getTarget() != null && element.getTarget().getType() != null) {
                    // Check if the invocation is on an HttpSession object
                    CtTypeReference<?> targetType = element.getTarget().getType();
                    return targetType.getQualifiedName().equals("jakarta.servlet.http.HttpSession") &&
                           element.getExecutable().getSimpleName().equals("putValue");
                }
                return false;
            }
        }).forEach(invocation -> {
            // Replace with setAttribute() call
            CtExecutableReference<?> setAttributeRef = factory.Executable().createReference(
                "jakarta.servlet.http.HttpSession.setAttribute(java.lang.String, java.lang.Object)");
            // Convert the arguments to proper CtExpression
            CtExpression<?> arg1 = invocation.getArguments().get(0);
            CtExpression<?> arg2 = invocation.getArguments().get(1);
            CtInvocation newInvocation = factory.Code().createInvocation(
                invocation.getTarget(),
                setAttributeRef,
                arg1,
                arg2
            );
            invocation.replace(newInvocation);
        });
    }
}