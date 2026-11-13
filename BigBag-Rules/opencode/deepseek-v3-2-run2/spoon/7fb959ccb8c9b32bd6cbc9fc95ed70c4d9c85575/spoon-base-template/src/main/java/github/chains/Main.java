package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-base.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        
        // Add processors for each type of breaking change
        launcher.addProcessor(new JettyConnectorProcessor());
        launcher.addProcessor(new ServletPackageProcessor());
        launcher.addProcessor(new AbstractHandlerProcessor());
        
        launcher.run();
        
        System.out.println("Transformation completed successfully!");
    }
    
    /**
     * Processor for handling Jetty connector and configuration changes
     */
    static class JettyConnectorProcessor extends AbstractProcessor<CtClass<?>> {
        @Override
        public void process(CtClass<?> ctClass) {
            System.out.println("Processing class: " + ctClass.getQualifiedName());
            
            // Process all constructor calls
            ctClass.getElements(new TypeFilter<>(CtConstructorCall.class)).forEach(this::processConstructorCall);
            
            // Process all method invocations
            ctClass.getElements(new TypeFilter<>(CtInvocation.class)).forEach(this::processMethodInvocation);
        }
        
        private void processConstructorCall(CtConstructorCall<?> constructor) {
            if (constructor.getType() == null) return;
            
            String typeName = constructor.getType().getQualifiedName();
            if ("org.eclipse.jetty.server.nio.SelectChannelConnector".equals(typeName)) {
                System.out.println("  Found SelectChannelConnector constructor - replacing with ServerConnector");
                
                // Replace constructor call - requires Server parameter
                CtConstructorCall<?> newConstructor = getFactory().createConstructorCall(
                    getFactory().Type().createReference("org.eclipse.jetty.server.ServerConnector")
                );
                // Need to pass Server instance - we'll add a placeholder comment
                newConstructor.addArgument(getFactory().createCodeSnippetExpression("/* TODO: Pass Server instance */ null"));
                constructor.replace(newConstructor);
            }
        }
        
        private void processMethodInvocation(CtInvocation<?> invocation) {
            if (invocation.getTarget() == null || invocation.getExecutable() == null) return;
            
            String methodName = invocation.getExecutable().getSimpleName();
            
            // Check if target is a Server instance
            CtTypeReference<?> targetType = invocation.getTarget().getType();
            if (targetType != null && "org.eclipse.jetty.server.Server".equals(targetType.getQualifiedName())) {
                if ("setSendServerVersion".equals(methodName) || "setSendDateHeader".equals(methodName)) {
                    System.out.println("  Found Server." + methodName + "() - method moved to HttpConfiguration in Jetty 11");
                    
                    // Create informative comment
                    String argValue = invocation.getArguments().isEmpty() ? "false" : invocation.getArguments().get(0).toString();
                    String comment = String.format(
                        "// Jetty 11: %s moved to HttpConfiguration\n" +
                        "// HttpConfiguration httpConfig = new HttpConfiguration();\n" +
                        "// httpConfig.%s(%s);",
                        methodName, methodName, argValue
                    );
                    
                    getFactory().createCodeSnippetStatement(comment).insertBefore(invocation);
                    invocation.delete();
                }
            }
            
            // Check if target is a Connector with setPort or getLocalPort
            // These should still work on ServerConnector
        }
    }
    
    /**
     * Processor for updating javax.servlet to jakarta.servlet
     */
    static class ServletPackageProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> typeRef) {
            String qualifiedName = typeRef.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("javax.servlet")) {
                System.out.println("Replacing javax.servlet type: " + qualifiedName + " -> jakarta.servlet");
                
                String newName = qualifiedName.replace("javax.servlet", "jakarta.servlet");
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(newName);
                typeRef.replace(newTypeRef);
            }
        }
    }
    
    /**
     * Processor for updating AbstractHandler.handle() method signatures
     */
    static class AbstractHandlerProcessor extends AbstractProcessor<CtMethod<?>> {
        @Override
        public void process(CtMethod<?> method) {
            // Check if method is named "handle"
            if (!"handle".equals(method.getSimpleName())) return;
            
            // Check if it has javax.servlet parameters
            List<CtParameter<?>> params = method.getParameters();
            boolean hasJavaxServlet = false;
            
            for (CtParameter<?> param : params) {
                CtTypeReference<?> type = param.getType();
                if (type != null && type.getQualifiedName() != null && 
                    type.getQualifiedName().startsWith("javax.servlet")) {
                    hasJavaxServlet = true;
                    break;
                }
            }
            
            if (hasJavaxServlet) {
                System.out.println("Found handle() method with javax.servlet parameters - updating to jakarta.servlet");
                
                // Update all javax.servlet parameter types to jakarta.servlet
                for (CtParameter<?> param : params) {
                    CtTypeReference<?> type = param.getType();
                    if (type != null && type.getQualifiedName() != null && 
                        type.getQualifiedName().startsWith("javax.servlet")) {
                        String newName = type.getQualifiedName().replace("javax.servlet", "jakarta.servlet");
                        param.setType(getFactory().Type().createReference(newName));
                    }
                }
            }
        }
    }
}