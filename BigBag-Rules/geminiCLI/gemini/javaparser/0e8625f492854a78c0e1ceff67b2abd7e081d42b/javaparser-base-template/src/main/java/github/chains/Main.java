package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        String projectRoot = "/workspace/jadler";
        
        // 1. Update JadlerHandler.java
        Path handlerPath = Paths.get(projectRoot, "jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java");
        CompilationUnit handlerCu = StaticJavaParser.parse(handlerPath);
        for (ImportDeclaration id : handlerCu.getImports()) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                id.setName(id.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        }
        Files.write(handlerPath, handlerCu.toString().getBytes());

        // 2. Update RequestUtils.java
        Path reqUtilsPath = Paths.get(projectRoot, "jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/RequestUtils.java");
        CompilationUnit reqUtilsCu = StaticJavaParser.parse(reqUtilsPath);
        for (ImportDeclaration id : reqUtilsCu.getImports()) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                id.setName(id.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        }
        Files.write(reqUtilsPath, reqUtilsCu.toString().getBytes());

        // 3. Update JettyStubHttpServer.java
        Path serverPath = Paths.get(projectRoot, "jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        CompilationUnit serverCu = StaticJavaParser.parse(serverPath);
        
        // Remove old connector import
        serverCu.getImports().removeIf(id -> id.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        // Add new imports
        serverCu.addImport("org.eclipse.jetty.server.ServerConnector");
        serverCu.addImport("org.eclipse.jetty.server.HttpConfiguration");
        serverCu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
        
        ClassOrInterfaceDeclaration serverClass = serverCu.getClassByName("JettyStubHttpServer").get();
        // Update field type
        for (FieldDeclaration fd : serverClass.getFields()) {
            if (fd.getVariable(0).getNameAsString().equals("httpConnector")) {
                fd.getVariable(0).setType("ServerConnector");
            }
        }
        
        // Update constructor body
        for (ConstructorDeclaration cd : serverClass.getConstructors()) {
            if (cd.getParameters().size() == 1 && cd.getParameter(0).getType().asString().equals("int")) {
                cd.setBody(StaticJavaParser.parseBlock(
                    "{\n" +
                    "    this.server = new Server();\n" +
                    "    HttpConfiguration httpConfig = new HttpConfiguration();\n" +
                    "    httpConfig.setSendServerVersion(false);\n" +
                    "    httpConfig.setSendDateHeader(true);\n" +
                    "    HttpConnectionFactory httpFactory = new HttpConnectionFactory(httpConfig);\n" +
                    "    this.httpConnector = new ServerConnector(this.server, httpFactory);\n" +
                    "    this.httpConnector.setPort(port);\n" +
                    "    server.addConnector(this.httpConnector);\n" +
                    "}"
                ));
            }
        }
        Files.write(serverPath, serverCu.toString().getBytes());

        // 4. Replace RequestUtilsTest.java completely
        Path testPath = Paths.get(projectRoot, "jadler-jetty/src/test/java/net/jadler/stubbing/server/jetty/RequestUtilsTest.java");
        String newTestClass = 
            "package net.jadler.stubbing.server.jetty;\n" +
            "\n" +
            "import net.jadler.Request;\n" +
            "import org.junit.Test;\n" +
            "import jakarta.servlet.http.HttpServletRequest;\n" +
            "import jakarta.servlet.ServletInputStream;\n" +
            "import jakarta.servlet.ReadListener;\n" +
            "import java.io.IOException;\n" +
            "import java.io.ByteArrayInputStream;\n" +
            "import java.net.URI;\n" +
            "import java.util.Collections;\n" +
            "import java.util.Vector;\n" +
            "\n" +
            "import static org.hamcrest.MatcherAssert.assertThat;\n" +
            "import static org.hamcrest.Matchers.contains;\n" +
            "import static org.hamcrest.Matchers.containsInAnyOrder;\n" +
            "import static org.hamcrest.Matchers.is;\n" +
            "import static org.mockito.Mockito.mock;\n" +
            "import static org.mockito.Mockito.when;\n" +
            "\n" +
            "public class RequestUtilsTest {\n" +
            "\n" +
            "    @Test\n" +
            "    public void method() throws IOException {\n" +
            "        HttpServletRequest httpRequest = prepareEmptyRequest();\n" +
            "        Request req = RequestUtils.convert(httpRequest);\n" +
            "        assertThat(req.getMethod(), is(\"POST\"));\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    public void body() throws IOException {\n" +
            "        HttpServletRequest httpRequest = prepareEmptyRequest();\n" +
            "        setBody(httpRequest, \"abcd\".getBytes());\n" +
            "        Request req = RequestUtils.convert(httpRequest);\n" +
            "        assertThat(req.getBodyAsString(), is(\"abcd\"));\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    public void headers() throws IOException {\n" +
            "        HttpServletRequest httpRequest = prepareEmptyRequest();\n" +
            "        when(httpRequest.getHeaderNames()).thenReturn(new Vector<String>(java.util.Arrays.asList(\"header1\", \"header2\")).elements());\n" +
            "        when(httpRequest.getHeaders(\"header1\")).thenReturn(new Vector<String>(java.util.Arrays.asList(\"value11\")).elements());\n" +
            "        when(httpRequest.getHeaders(\"header2\")).thenReturn(new Vector<String>(java.util.Arrays.asList(\"value21\", \"value22\")).elements());\n" +
            "\n" +
            "        Request req = RequestUtils.convert(httpRequest);\n" +
            "\n" +
            "        assertThat(req.getHeaders().getKeys(), containsInAnyOrder(\"header1\", \"header2\"));\n" +
            "        assertThat(req.getHeaders().getValues(\"header1\"), contains(\"value11\"));\n" +
            "        assertThat(req.getHeaders().getValues(\"header2\"), contains(\"value21\", \"value22\"));\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    public void parameters() throws IOException {\n" +
            "        HttpServletRequest httpRequest = prepareEmptyRequest();\n" +
            "        when(httpRequest.getQueryString()).thenReturn(\"a=1&b=3\");\n" +
            "        setBody(httpRequest, \"a=2\".getBytes());\n" +
            "        when(httpRequest.getHeaderNames()).thenReturn(new Vector<String>(java.util.Arrays.asList(\"content-type\")).elements());\n" +
            "        when(httpRequest.getHeaders(\"content-type\")).thenReturn(new Vector<String>(java.util.Arrays.asList(\"application/x-www-form-urlencoded\")).elements());\n" +
            "\n" +
            "        Request req = RequestUtils.convert(httpRequest);\n" +
            "        assertThat(req.getParameters().getKeys(), containsInAnyOrder(\"a\", \"b\"));\n" +
            "        assertThat(req.getParameters().getValues(\"a\"), contains(\"1\", \"2\"));\n" +
            "        assertThat(req.getParameters().getValues(\"b\"), contains(\"3\"));\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    public void parametersURLEncoded() throws IOException {\n" +
            "        HttpServletRequest httpRequest = prepareEmptyRequest();\n" +
            "        when(httpRequest.getQueryString()).thenReturn(\"param1%20name=param1%20value\");\n" +
            "        setBody(httpRequest, \"param2%20name=param2%20value\".getBytes());\n" +
            "        when(httpRequest.getHeaderNames()).thenReturn(new Vector<String>(java.util.Arrays.asList(\"content-type\")).elements());\n" +
            "        when(httpRequest.getHeaders(\"content-type\")).thenReturn(new Vector<String>(java.util.Arrays.asList(\"application/x-www-form-urlencoded\")).elements());\n" +
            "\n" +
            "        Request req = RequestUtils.convert(httpRequest);\n" +
            "        assertThat(req.getParameters().getKeys(), containsInAnyOrder(\"param1%20name\", \"param2%20name\"));\n" +
            "        assertThat(req.getParameters().getValues(\"param1%20name\"), contains(\"param1%20value\"));\n" +
            "        assertThat(req.getParameters().getValues(\"param2%20name\"), contains(\"param2%20value\"));\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    public void uri() throws IOException {\n" +
            "        HttpServletRequest httpRequest = prepareEmptyRequest();\n" +
            "        when(httpRequest.getScheme()).thenReturn(\"https\");\n" +
            "        when(httpRequest.getServerName()).thenReturn(\"example.com\");\n" +
            "        when(httpRequest.getServerPort()).thenReturn(1234);\n" +
            "        when(httpRequest.getRequestURI()).thenReturn(\"/test/a/b\");\n" +
            "        when(httpRequest.getRequestURL()).thenReturn(new StringBuffer(\"https://example.com:1234/test/a/b\"));\n" +
            "        when(httpRequest.getQueryString()).thenReturn(\"a=1\");\n" +
            "\n" +
            "        Request req = RequestUtils.convert(httpRequest);\n" +
            "        assertThat(req.getURI(), is(URI.create(\"https://example.com:1234/test/a/b?a=1\")));\n" +
            "    }\n" +
            "\n" +
            "    @Test\n" +
            "    public void uriURLEncoded() throws IOException {\n" +
            "        HttpServletRequest httpRequest = prepareEmptyRequest();\n" +
            "        when(httpRequest.getScheme()).thenReturn(\"https\");\n" +
            "        when(httpRequest.getServerName()).thenReturn(\"example.com\");\n" +
            "        when(httpRequest.getServerPort()).thenReturn(1234);\n" +
            "        when(httpRequest.getRequestURI()).thenReturn(\"/te%20st/a/%20/b\");\n" +
            "        when(httpRequest.getRequestURL()).thenReturn(new StringBuffer(\"https://example.com:1234/te%20st/a/%20/b\"));\n" +
            "        when(httpRequest.getQueryString()).thenReturn(\"a=1&param%20name=param%20value\");\n" +
            "\n" +
            "        Request req = RequestUtils.convert(httpRequest);\n" +
            "        assertThat(req.getURI(), is(URI.create(\"https://example.com:1234/te%20st/a/%20/b?a=1&param%20name=param%20value\")));\n" +
            "    }\n" +
            "\n" +
            "    private HttpServletRequest prepareEmptyRequest() throws IOException {\n" +
            "        HttpServletRequest res = mock(HttpServletRequest.class);\n" +
            "        when(res.getMethod()).thenReturn(\"POST\");\n" +
            "        when(res.getRequestURL()).thenReturn(new StringBuffer(\"http://localhost/\"));\n" +
            "        when(res.getHeaderNames()).thenReturn(new Vector<String>().elements());\n" +
            "        setBody(res, new byte[0]);\n" +
            "        return res;\n" +
            "    }\n" +
            "    \n" +
            "    private void setBody(HttpServletRequest request, final byte[] content) throws IOException {\n" +
            "        final ByteArrayInputStream bais = new ByteArrayInputStream(content);\n" +
            "        ServletInputStream sis = new ServletInputStream() {\n" +
            "            @Override public int read() { return bais.read(); }\n" +
            "            @Override public boolean isFinished() { return bais.available() == 0; }\n" +
            "            @Override public boolean isReady() { return true; }\n" +
            "            @Override public void setReadListener(ReadListener readListener) {}\n" +
            "        };\n" +
            "        when(request.getInputStream()).thenReturn(sis);\n" +
            "    }\n" +
            "}";
        CompilationUnit testCu = StaticJavaParser.parse(newTestClass);
        Files.write(testPath, testCu.toString().getBytes());
    }
}
