package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Refactoring Processor for Jetty AbstractHandler Migration.
 * 
 * Diff Analysis:
 * - CLASS AbstractHandler [MODIFIED] changes=METHOD_ABSTRACT_ADDED_IN_IMPLEMENTED_INTERFACE
 * 
 * Reasoning:
 * The diff indicates that `AbstractHandler` (or an interface it implements) has introduced a new 
 * abstract method. In the context of Jetty migrations (e.g., Jetty 11 to 12), this typically 
 * corresponds to the `handle` method signature changing completely (e.g., arguments changing from 
 * (String, Request, HttpServletRequest, HttpServletResponse) to (Request, Response, Callback)).
 * 
 * Existing subclasses extending `AbstractHandler` will fail to compile because:
 * 1. They implement the old `handle` method (which is no longer an override).
 * 2. They fail to implement the new `handle` abstract method.
 * 
 * Strategy:
 * 1. Identify concrete classes extending `AbstractHandler`.
 * 2. Detect the old `handle` method (4 arguments).
 * 3. Refactor it to the new signature (boolean return, 3 arguments) to satisfy the compiler 
 *    and preserve the body logic for manual review.
 */
public class JettyHandlerRefactoring {

    public static class AbstractHandlerProcessor extends AbstractProcessor<CtClass<?>> {
        
        @Override
        public boolean isToBeProcessed(CtClass<?> candidate) {
            // 1. Must be a Class
            if (!candidate.isClass()) return false;

            // 2. Check Superclass (Defensive for NoClasspath)
            CtTypeReference<?> superType = candidate.getSuperClass();
            if (superType == null) return false;

            // Loose matching to catch "AbstractHandler", "org.eclipse.jetty...AbstractHandler", etc.
            // This avoids failing if the full classpath isn't available.
            String superName = superType.getQualifiedName();
            return superName != null && superName.contains("AbstractHandler");
        }

        @Override
        public void process(CtClass<?> ctClass) {
            Factory factory = getFactory();
            
            // Search for the old 'handle' method signature:
            // void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            CtMethod<?> oldHandle = null;
            
            for (CtMethod<?> method : ctClass.getMethods()) {
                // Check name
                if (!"handle".equals(method.getSimpleName())) continue;
                
                // Check arg count (Old signature has 4, New has 3)
                if (method.getParameters().size() == 4) {
                    oldHandle = method;
                    break;
                }
            }

            // If we found the old signature, migrate it to the new structure
            if (oldHandle != null) {
                refactorHandleMethod(factory, oldHandle);
            }
        }

        private void refactorHandleMethod(Factory factory, CtMethod<?> method) {
            // 1. Change Return Type: void -> boolean
            CtTypeReference<Boolean> boolType = factory.Type().booleanPrimitiveType();
            method.setType(boolType);

            // 2. Construct New Parameters
            // New Signature (Jetty 12+ style): (Request request, Response response, Callback callback)
            // We use string references to avoid dependency on the actual library JARs during refactoring.
            List<CtParameter<?>> newParams = new ArrayList<>();
            
            // Param 1: org.eclipse.jetty.server.Request
            CtTypeReference<?> reqType = factory.Type().createReference("org.eclipse.jetty.server.Request");
            CtParameter<?> paramReq = factory.Executable().createParameter(null, reqType, "request");
            newParams.add(paramReq);

            // Param 2: org.eclipse.jetty.server.Response
            CtTypeReference<?> resType = factory.Type().createReference("org.eclipse.jetty.server.Response");
            CtParameter<?> paramRes = factory.Executable().createParameter(null, resType, "response");
            newParams.add(paramRes);
            
            // Param 3: org.eclipse.jetty.util.Callback
            CtTypeReference<?> cbType = factory.Type().createReference("org.eclipse.jetty.util.Callback");
            CtParameter<?> paramCb = factory.Executable().createParameter(null, cbType, "callback");
            newParams.add(paramCb);

            method.setParameters(newParams);

            // 3. Update Thrown Exceptions (Simplification)
            // Replace specific ServletExceptions with generic Exception to match new interface contract
            Set<CtTypeReference<? extends Throwable>> thrownTypes = method.getThrownTypes();
            thrownTypes.clear();
            thrownTypes.add(factory.Type().createReference("java.lang.Exception"));
            method.setThrownTypes(thrownTypes);

            // 4. Body Injection
            CtBlock<?> body = method.getBody();
            if (body != null) {
                // Add TODO comment at the top
                String todoMsg = " TODO: Migration Required. Signature changed from (String, Request, HttpServletRequest, HttpServletResponse) to (Request, Response, Callback).";
                CtComment todo = factory.Code().createComment(todoMsg, CtComment.CommentType.INLINE);
                body.insertBegin(todo);

                // Add 'return false;' at the end to satisfy the boolean return type
                // 'false' is a safe default implying the request wasn't fully handled or needs chain processing
                body.addStatement(factory.Code().createReturn(factory.Code().createLiteral(false)));
            }
            
            System.out.println("Refactored 'handle' method in class: " + method.getDeclaringType().getQualifiedName());
        }
    }

    public static void main(String[] args) {
        // Default configuration (can be overridden by args)
        String inputPath = "/home/kth/Documents/last_transformer/output/0e8625f492854a78c0e1ceff67b2abd7e081d42b/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0e8625f492854a78c0e1ceff67b2abd7e081d42b/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/0e8625f492854a78c0e1ceff67b2abd7e081d42b/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/0e8625f492854a78c0e1ceff67b2abd7e081d42b/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ / Robust Refactoring
        
        // 1. Enable comment processing to preserve existing docs
        launcher.getEnvironment().setCommentEnabled(true);
        
        // 2. Force usage of SniperJavaPrettyPrinter.
        // This ensures that lines of code NOT modified by the processor remain byte-for-byte identical 
        // (preserving indentation, formatting, weird spacing).
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // 3. Enable NoClasspath mode.
        // This allows the refactoring to run even if the new Jetty JARs are not yet in the project's classpath.
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new AbstractHandlerProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete. Output generated in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}