package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtExpression;

import java.util.List;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Jetty 8/9 to Jetty 11 API in: " + sourceDir);
        
        // Create Spoon launcher
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(sourceDir);
        
        // Build model
        CtModel model = launcher.buildModel();
        Factory factory = launcher.getFactory();
        
        int transformations = 0;
        
        // 1. Transform SelectChannelConnector to ServerConnector
        transformations += transformSelectChannelConnector(model, factory);
        
        // 2. Transform Server configuration methods using simpler approach
        transformations += transformServerConfigurationMethodsSimple(model, factory);
        
        // 3. Transform getLocalPort() calls - simpler approach
        transformations += transformGetLocalPortSimple(model, factory);
        
        // 4. Transform setPort() calls - simpler approach  
        transformations += transformSetPortSimple(model, factory);
        
        System.out.println("Total transformations applied: " + transformations);
        
        // Write transformed code
        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }
    
    private static int transformSelectChannelConnector(CtModel model, Factory factory) {
        int count = 0;
        
        // Find constructor calls to SelectChannelConnector
        List<CtConstructorCall> consCalls = model.getElements(new TypeFilter<CtConstructorCall>(CtConstructorCall.class) {
            @Override
            public boolean matches(CtConstructorCall element) {
                CtTypeReference type = element.getType();
                return type != null && 
                       "org.eclipse.jetty.server.nio.SelectChannelConnector".equals(type.getQualifiedName());
            }
        });
        
        for (CtConstructorCall cons : consCalls) {
            // Replace with ServerConnector(server) - we need to find server variable
            // Create code snippet for now
            CtCodeSnippetExpression snippet = factory.Code().createCodeSnippetExpression(
                "new ServerConnector(server)"
            );
            cons.replace(snippet);
            count++;
        }
        
        System.out.println("Transformed " + count + " SelectChannelConnector constructor calls");
        return count;
    }
    
    private static int transformServerConfigurationMethodsSimple(CtModel model, Factory factory) {
        int count = 0;
        
        // Find method calls to setSendServerVersion or setSendDateHeader
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (element.getExecutable() == null) return false;
                String methodName = element.getExecutable().getSimpleName();
                return methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader");
            }
        });
        
        for (CtInvocation<?> inv : invocations) {
            String methodName = inv.getExecutable().getSimpleName();
            CtExpression<?> target = inv.getTarget();
            if (target != null) {
                // Create: server.getBean(HttpConfiguration.class).methodName(...)
                String snippet = target.toString() + ".getBean(HttpConfiguration.class)." + methodName + "(";
                // Add arguments
                for (int i = 0; i < inv.getArguments().size(); i++) {
                    if (i > 0) snippet += ", ";
                    snippet += inv.getArguments().get(i).toString();
                }
                snippet += ")";
                
                CtCodeSnippetExpression replacement = factory.Code().createCodeSnippetExpression(snippet);
                inv.replace(replacement);
                count++;
            }
        }
        
        System.out.println("Transformed " + count + " Server configuration method calls");
        return count;
    }
    
    private static int transformGetLocalPortSimple(CtModel model, Factory factory) {
        int count = 0;
        
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (element.getExecutable() == null) return false;
                return element.getExecutable().getSimpleName().equals("getLocalPort");
            }
        });
        
        for (CtInvocation<?> inv : invocations) {
            if (inv.getTarget() != null) {
                String targetExpr = inv.getTarget().toString();
                String snippet = "((" + targetExpr + " instanceof org.eclipse.jetty.server.NetworkConnector) ? " +
                               "((org.eclipse.jetty.server.NetworkConnector)" + targetExpr + ").getLocalPort() : " +
                               targetExpr + ".getLocalPort())";
                
                CtCodeSnippetExpression replacement = factory.Code().createCodeSnippetExpression(snippet);
                inv.replace(replacement);
                count++;
            }
        }
        
        System.out.println("Transformed " + count + " getLocalPort() calls");
        return count;
    }
    
    private static int transformSetPortSimple(CtModel model, Factory factory) {
        int count = 0;
        
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation<?> element) {
                if (element.getExecutable() == null) return false;
                return element.getExecutable().getSimpleName().equals("setPort");
            }
        });
        
        for (CtInvocation<?> inv : invocations) {
            if (inv.getTarget() != null && inv.getArguments().size() == 1) {
                String targetExpr = inv.getTarget().toString();
                String arg = inv.getArguments().get(0).toString();
                
                String snippet = "((" + targetExpr + " instanceof org.eclipse.jetty.server.ServerConnector) ? " +
                               "((org.eclipse.jetty.server.ServerConnector)" + targetExpr + ").setPort(" + arg + ") : " +
                               targetExpr + ".setPort(" + arg + "))";
                
                CtCodeSnippetExpression replacement = factory.Code().createCodeSnippetExpression(snippet);
                inv.replace(replacement);
                count++;
            }
        }
        
        System.out.println("Transformed " + count + " setPort() calls");
        return count;
    }
}