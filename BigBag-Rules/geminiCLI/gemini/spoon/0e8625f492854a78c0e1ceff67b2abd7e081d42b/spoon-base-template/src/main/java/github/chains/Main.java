package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        
        CtModel model = launcher.buildModel();

        // 1. Transform JettyStubHttpServer
        CtClass<?> serverClass = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return "JettyStubHttpServer".equals(element.getSimpleName());
            }
        }).get(0);

        // Transform constructor JettyStubHttpServer(int port)
        for (CtConstructor<?> constructor : serverClass.getConstructors()) {
            if (constructor.getParameters().size() == 1) {
                CtBlock<?> body = constructor.getBody();
                List<CtStatement> statements = body.getStatements();
                
                CtStatement first = statements.get(0);
                
                body.setStatements(java.util.Collections.singletonList(first));
                
                CtCodeSnippetStatement snippet1 = launcher.getFactory().Core().createCodeSnippetStatement();
                snippet1.setValue("org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration()");
                body.addStatement(snippet1);
                
                CtCodeSnippetStatement snippet2 = launcher.getFactory().Core().createCodeSnippetStatement();
                snippet2.setValue("httpConfig.setSendServerVersion(false)");
                body.addStatement(snippet2);
                
                CtCodeSnippetStatement snippet3 = launcher.getFactory().Core().createCodeSnippetStatement();
                snippet3.setValue("httpConfig.setSendDateHeader(true)");
                body.addStatement(snippet3);
                
                CtCodeSnippetStatement snippet4 = launcher.getFactory().Core().createCodeSnippetStatement();
                snippet4.setValue("this.httpConnector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig))");
                body.addStatement(snippet4);
                
                CtCodeSnippetStatement snippet5 = launcher.getFactory().Core().createCodeSnippetStatement();
                snippet5.setValue("((org.eclipse.jetty.server.ServerConnector)this.httpConnector).setPort(port)");
                body.addStatement(snippet5);
                
                CtCodeSnippetStatement snippet6 = launcher.getFactory().Core().createCodeSnippetStatement();
                snippet6.setValue("server.addConnector(this.httpConnector)");
                body.addStatement(snippet6);
            }
        }

        // Transform getPort()
        CtMethod<?> getPortMethod = serverClass.getMethodsByName("getPort").get(0);
        CtCodeSnippetStatement getPortSnippet = launcher.getFactory().Core().createCodeSnippetStatement();
        getPortSnippet.setValue("return ((org.eclipse.jetty.server.ServerConnector) httpConnector).getLocalPort()");
        CtBlock<?> getPortBody = launcher.getFactory().Core().createBlock();
        getPortBody.addStatement(getPortSnippet);
        getPortMethod.setBody(getPortBody);

        // Change all javax.servlet to jakarta.servlet
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class))) {
            if (ref.getQualifiedName().startsWith("javax.servlet")) {
                CtPackageReference pkg = ref.getPackage();
                if (pkg != null) {
                    pkg.setSimpleName(pkg.getSimpleName().replace("javax.servlet", "jakarta.servlet"));
                }
            }
        }

        launcher.prettyprint();
        System.out.println("Spoon transformation complete.");
    }
}
