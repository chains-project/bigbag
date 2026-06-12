package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.code.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();

        CtClass<?> clazz = launcher.getFactory().Class().get("net.jadler.stubbing.server.jetty.JettyStubHttpServer");
        
        CtConstructor<?> ctor = clazz.getConstructors().stream().filter(c -> c.getParameters().size() == 1).findFirst().get();
        
        String newBody = "this.server = new org.eclipse.jetty.server.Server();\n"
            + "org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();\n"
            + "httpConfig.setSendServerVersion(false);\n"
            + "httpConfig.setSendDateHeader(true);\n"
            + "org.eclipse.jetty.server.ServerConnector connector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig));\n"
            + "connector.setPort(port);\n"
            + "this.httpConnector = connector;\n"
            + "this.server.addConnector(this.httpConnector)";
        
        CtCodeSnippetStatement snippet = launcher.getFactory().Code().createCodeSnippetStatement(newBody);
        CtBlock<?> block = launcher.getFactory().Core().createBlock();
        block.addStatement(snippet);
        ctor.setBody(block);
        
        CtMethod<?> getPort = clazz.getMethodsByName("getPort").get(0);
        CtCodeSnippetStatement getPortSnippet = launcher.getFactory().Code().createCodeSnippetStatement("return ((org.eclipse.jetty.server.ServerConnector)this.httpConnector).getLocalPort()");
        CtBlock<?> getPortBlock = launcher.getFactory().Core().createBlock();
        getPortBlock.addStatement(getPortSnippet);
        getPort.setBody(getPortBlock);

        spoon.reflect.cu.CompilationUnit cu = launcher.getFactory().CompilationUnit().getOrCreate(clazz);
        List<spoon.reflect.declaration.CtImport> newImports = cu.getImports().stream()
                .filter(i -> !i.toString().contains("SelectChannelConnector"))
                .collect(Collectors.toList());
        cu.setImports(newImports);

        launcher.setSourceOutputDirectory("/workspace/jadler/jadler-jetty/src/main/java");
        launcher.prettyprint();
    }
}
