package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setNoClasspath(true);
        
        launcher.buildModel();
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();
        
        // 1. Replace javax.servlet with jakarta.servlet in imports and remove SelectChannelConnector
        for (spoon.reflect.cu.CompilationUnit cu : factory.CompilationUnit().getMap().values()) {
            List<CtImport> imps = new ArrayList<>(cu.getImports());
            imps.removeIf(i -> {
                String str = i.toString();
                return str.contains("javax.servlet") || str.contains("SelectChannelConnector");
            });
            cu.setImports(imps);
        }
        
        // 2. Modify JettyStubHttpServer
        List<CtClass<?>> classes = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class));
        CtClass<?> jettyServerClass = null;
        for (CtClass<?> c : classes) {
            if (c.getSimpleName().equals("JettyStubHttpServer")) {
                jettyServerClass = c;
                break;
            }
        }
        
        if (jettyServerClass != null) {
            // 2a. Modify constructor
            Set<? extends CtConstructor<?>> constructors = jettyServerClass.getConstructors();
            for (CtConstructor<?> constructor : constructors) {
                if (constructor.getParameters().size() == 1) {
                    CtBlock<?> body = factory.Core().createBlock();
                    CtCodeSnippetStatement stmt = factory.Code().createCodeSnippetStatement(
                        "this.server = new org.eclipse.jetty.server.Server();\n" +
                        "org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();\n" +
                        "httpConfig.setSendServerVersion(false);\n" +
                        "httpConfig.setSendDateHeader(true);\n" +
                        "this.httpConnector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig));\n" +
                        "((org.eclipse.jetty.server.ServerConnector) this.httpConnector).setPort(port);\n" +
                        "this.server.addConnector(this.httpConnector)"
                    );
                    body.addStatement(stmt);
                    constructor.setBody(body);
                }
            }
            
            // 2b. Modify getPort
            List<CtMethod<?>> methods = jettyServerClass.getMethodsByName("getPort");
            if (!methods.isEmpty()) {
                CtMethod<?> getPortMethod = methods.get(0);
                CtBlock<?> body = factory.Core().createBlock();
                CtCodeSnippetStatement stmt = factory.Code().createCodeSnippetStatement(
                    "return ((org.eclipse.jetty.server.ServerConnector) httpConnector).getLocalPort()"
                );
                body.addStatement(stmt);
                getPortMethod.setBody(body);
            }
        }
        
        // 3. Also update javax.servlet to jakarta.servlet in all parameter types and references
        List<CtTypeReference<?>> refs = model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class));
        for (CtTypeReference<?> ref : refs) {
            if (ref.getQualifiedName() != null && ref.getQualifiedName().startsWith("javax.servlet")) {
                if (ref.getPackage() != null) {
                    String pkgName = ref.getPackage().getQualifiedName();
                    String newPkgName = pkgName.replace("javax.servlet", "jakarta.servlet");
                    ref.setPackage(factory.Package().createReference(newPkgName));
                }
            }
        }
        
        // 4. Fix req variable type inside JadlerHandler
        List<CtLocalVariable<?>> locals = model.getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> local : locals) {
            if (local.getSimpleName().equals("req")) {
                local.setType(factory.Type().createReference("net.jadler.Request"));
            }
        }
        
        launcher.prettyprint();
        System.out.println("Transformation complete.");
    }
}
