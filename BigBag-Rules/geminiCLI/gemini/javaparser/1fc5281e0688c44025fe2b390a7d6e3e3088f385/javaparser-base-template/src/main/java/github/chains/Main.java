package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        fixJadlerHandler();
        fixRequestUtils();
        fixJettyStubHttpServer();
        fixRequestUtilsTest();
        System.out.println("JavaParser transformation completed.");
    }

    private static void fixJadlerHandler() throws Exception {
        File file = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        for (ImportDeclaration id : cu.getImports()) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                id.setName(id.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        }

        Files.write(file.toPath(), cu.toString().getBytes());
    }

    private static void fixRequestUtils() throws Exception {
        File file = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/RequestUtils.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        for (ImportDeclaration id : cu.getImports()) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                id.setName(id.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        }

        Files.write(file.toPath(), cu.toString().getBytes());
    }

    private static void fixJettyStubHttpServer() throws Exception {
        File file = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        // Remove SelectChannelConnector import
        cu.getImports().removeIf(id -> id.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));

        ClassOrInterfaceDeclaration cls = cu.getClassByName("JettyStubHttpServer").get();

        // Rewrite constructor
        ConstructorDeclaration ctor = cls.getConstructors().stream().filter(c -> c.getParameters().size() == 1).findFirst().get();
        BlockStmt body = StaticJavaParser.parseBlock(
                "{\n" +
                "    this.server = new Server();\n" +
                "    org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();\n" +
                "    httpConfig.setSendServerVersion(false);\n" +
                "    httpConfig.setSendDateHeader(true);\n" +
                "    org.eclipse.jetty.server.ServerConnector connector = new org.eclipse.jetty.server.ServerConnector(this.server, new org.eclipse.jetty.server.HttpConnectionFactory(httpConfig));\n" +
                "    connector.setPort(port);\n" +
                "    this.httpConnector = connector;\n" +
                "    server.addConnector(this.httpConnector);\n" +
                "}"
        );
        ctor.setBody(body);

        // Rewrite getPort()
        MethodDeclaration getPort = cls.getMethodsByName("getPort").get(0);
        getPort.setBody(StaticJavaParser.parseBlock(
                "{\n" +
                "    return ((org.eclipse.jetty.server.ServerConnector) httpConnector).getLocalPort();\n" +
                "}"
        ));

        Files.write(file.toPath(), cu.toString().getBytes());
    }

    private static void fixRequestUtilsTest() throws Exception {
        File file = new File("/workspace/jadler/jadler-jetty/src/test/java/net/jadler/stubbing/server/jetty/RequestUtilsTest.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        ClassOrInterfaceDeclaration cls = cu.getClassByName("RequestUtilsTest").get();

        // Add createRequest method
        MethodDeclaration createRequest = StaticJavaParser.parseMethodDeclaration(
                "private jakarta.servlet.http.HttpServletRequest createRequest(final org.springframework.mock.web.MockHttpServletRequest mock) {\n" +
                "    return (jakarta.servlet.http.HttpServletRequest) java.lang.reflect.Proxy.newProxyInstance(\n" +
                "            RequestUtilsTest.class.getClassLoader(),\n" +
                "            new Class<?>[]{jakarta.servlet.http.HttpServletRequest.class},\n" +
                "            new java.lang.reflect.InvocationHandler() {\n" +
                "                @Override\n" +
                "                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {\n" +
                "                    if (\"getInputStream\".equals(method.getName())) {\n" +
                "                        final java.io.InputStream is = mock.getInputStream();\n" +
                "                        return new jakarta.servlet.ServletInputStream() {\n" +
                "                            @Override public int read() throws java.io.IOException { return is.read(); }\n" +
                "                            @Override public int read(byte[] b, int off, int len) throws java.io.IOException { return is.read(b, off, len); }\n" +
                "                            @Override public boolean isFinished() { return false; }\n" +
                "                            @Override public boolean isReady() { return true; }\n" +
                "                            @Override public void setReadListener(jakarta.servlet.ReadListener readListener) {}\n" +
                "                        };\n" +
                "                    }\n" +
                "                    java.lang.reflect.Method m = mock.getClass().getMethod(method.getName(), method.getParameterTypes());\n" +
                "                    return m.invoke(mock, args);\n" +
                "                }\n" +
                "            }\n" +
                "    );\n" +
                "}"
        );
        cls.addMember(createRequest);

        // Find all RequestUtils.convert(httpRequest) and replace with RequestUtils.convert(createRequest(httpRequest))
        cu.findAll(MethodCallExpr.class).forEach(mc -> {
            if (mc.getNameAsString().equals("convert") && mc.getScope().isPresent() && mc.getScope().get().toString().equals("RequestUtils")) {
                Expression arg = mc.getArgument(0);
                MethodCallExpr wrapped = new MethodCallExpr("createRequest", arg);
                mc.setArgument(0, wrapped);
            }
        });

        Files.write(file.toPath(), cu.toString().getBytes());
    }
}
