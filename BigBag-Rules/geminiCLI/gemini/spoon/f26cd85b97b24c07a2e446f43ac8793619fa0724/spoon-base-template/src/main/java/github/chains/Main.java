package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        
        for (CtTypeReference<?> typeRef : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (typeRef.getQualifiedName().startsWith("javax.servlet")) {
                String newQualifiedName = typeRef.getQualifiedName().replace("javax.servlet", "jakarta.servlet");
                CtTypeReference<?> newTypeRef = launcher.getFactory().Type().createReference(newQualifiedName);
                typeRef.replace(newTypeRef);
            }
        }
        
        CtClass<?> serverClass = launcher.getFactory().Class().get("net.jadler.stubbing.server.jetty.JettyStubHttpServer");
        
        if (serverClass != null) {
            CtConstructor<?> constructor = serverClass.getConstructors().stream().filter(c -> c.getParameters().size() == 1).findFirst().get();
            constructor.setBody(launcher.getFactory().Code().createCodeSnippetStatement(
                    "this.server = new org.eclipse.jetty.server.Server();\n" +
                    "org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();\n" +
                    "httpConfig.setSendServerVersion(false);\n" +
                    "httpConfig.setSendDateHeader(true);\n" +
                    "this.httpConnector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig));\n" +
                    "((org.eclipse.jetty.server.ServerConnector)this.httpConnector).setPort(port);\n" +
                    "server.addConnector(this.httpConnector)"
            ));
            
            CtMethod<?> getPortMethod = serverClass.getMethodsByName("getPort").get(0);
            getPortMethod.setBody(launcher.getFactory().Code().createCodeSnippetStatement(
                    "return ((org.eclipse.jetty.server.ServerConnector) httpConnector).getLocalPort()"
            ));
        }

        CtClass<?> handlerClass = launcher.getFactory().Class().get("net.jadler.stubbing.server.jetty.JadlerHandler");
        if (handlerClass != null) {
             CtMethod<?> handleMethod = handlerClass.getMethodsByName("handle").get(0);
             handleMethod.setBody(launcher.getFactory().Code().createCodeSnippetStatement(
                 "final net.jadler.Request req = net.jadler.stubbing.server.jetty.RequestUtils.convert(request);\n" +
                 "final net.jadler.stubbing.StubResponse stubResponse = this.requestManager.provideStubResponseFor(req);\n" +
                 "response.setStatus(stubResponse.getStatus());\n" +
                 "this.insertResponseHeaders(stubResponse.getHeaders(), response);\n" +
                 "baseRequest.setHandled(true);\n" +
                 "this.processDelay(stubResponse.getDelay());\n" +
                 "this.insertResponseBody(stubResponse.getBody(), response)"
             ));
        }

        for (spoon.reflect.declaration.CtCompilationUnit cu : launcher.getFactory().CompilationUnit().getMap().values()) {
            cu.getImports().clear();
        }

        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.prettyprint();
    }
}