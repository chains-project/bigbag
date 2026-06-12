package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String basePath = "/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/";
        
        // 1. JadlerHandler.java
        File jadlerHandlerFile = new File(basePath + "JadlerHandler.java");
        CompilationUnit cu1 = StaticJavaParser.parse(jadlerHandlerFile);
        cu1.getImports().removeIf(i -> i.getNameAsString().startsWith("javax.servlet"));
        cu1.addImport("jakarta.servlet.ServletException");
        cu1.addImport("jakarta.servlet.http.HttpServletRequest");
        cu1.addImport("jakarta.servlet.http.HttpServletResponse");
        
        MethodDeclaration handleMethod = cu1.getClassByName("JadlerHandler").get().getMethodsByName("handle").get(0);
        handleMethod.getParameter(2).setType("HttpServletRequest");
        handleMethod.getParameter(3).setType("HttpServletResponse");
        Files.write(jadlerHandlerFile.toPath(), cu1.toString().getBytes());

        // 2. RequestUtils.java
        File requestUtilsFile = new File(basePath + "RequestUtils.java");
        CompilationUnit cu2 = StaticJavaParser.parse(requestUtilsFile);
        cu2.getImports().removeIf(i -> i.getNameAsString().startsWith("javax.servlet"));
        cu2.addImport("jakarta.servlet.http.HttpServletRequest");
        
        MethodDeclaration convertMethod = cu2.getClassByName("RequestUtils").get().getMethodsByName("convert").get(0);
        convertMethod.getParameter(0).setType("HttpServletRequest");
        
        MethodDeclaration getQueryStringMethod = cu2.getClassByName("RequestUtils").get().getMethodsByName("getQueryString").get(0);
        getQueryStringMethod.getParameter(0).setType("HttpServletRequest");

        MethodDeclaration addHeadersMethod = cu2.getClassByName("RequestUtils").get().getMethodsByName("addHeaders").get(0);
        addHeadersMethod.getParameter(1).setType("HttpServletRequest");
        Files.write(requestUtilsFile.toPath(), cu2.toString().getBytes());

        // 3. JettyStubHttpServer.java
        File jettyServerFile = new File(basePath + "JettyStubHttpServer.java");
        CompilationUnit cu3 = StaticJavaParser.parse(jettyServerFile);
        cu3.getImports().removeIf(i -> i.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        cu3.addImport("org.eclipse.jetty.server.ServerConnector");
        cu3.addImport("org.eclipse.jetty.server.HttpConfiguration");
        cu3.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
        
        ConstructorDeclaration ctor = cu3.getClassByName("JettyStubHttpServer").get().getConstructorByParameterTypes("int").get();
        BlockStmt body = new BlockStmt();
        body.addStatement("this.server = new Server();");
        body.addStatement("HttpConfiguration httpConfig = new HttpConfiguration();");
        body.addStatement("httpConfig.setSendServerVersion(false);");
        body.addStatement("httpConfig.setSendDateHeader(true);");
        body.addStatement("ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));");
        body.addStatement("connector.setPort(port);");
        body.addStatement("this.httpConnector = connector;");
        body.addStatement("server.addConnector(this.httpConnector);");
        ctor.setBody(body);
        
        MethodDeclaration getPortMethod = cu3.getClassByName("JettyStubHttpServer").get().getMethodsByName("getPort").get(0);
        getPortMethod.setBody(StaticJavaParser.parseBlock("{ return ((ServerConnector) httpConnector).getLocalPort(); }"));
        Files.write(jettyServerFile.toPath(), cu3.toString().getBytes());

        // 4. RequestUtilsTest.java
        String testPath = "/workspace/jadler/jadler-jetty/src/test/java/net/jadler/stubbing/server/jetty/RequestUtilsTest.java";
        File requestUtilsTestFile = new File(testPath);
        String testContent = new String(Files.readAllBytes(Paths.get("/workspace/RequestUtilsTest_new.java")));
        
        CompilationUnit parsedTest = StaticJavaParser.parse(testContent);
        Files.write(requestUtilsTestFile.toPath(), parsedTest.toString().getBytes());

        System.out.println("Transformation complete.");
    }
}
