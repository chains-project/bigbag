package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.File;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws Exception {
        // Fix JadlerHandler
        File jadlerHandlerFile = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java");
        CompilationUnit cuHandler = StaticJavaParser.parse(jadlerHandlerFile);
        cuHandler.getImports().forEach(i -> {
            if (i.getNameAsString().startsWith("javax.servlet")) {
                i.setName(i.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        });
        Files.write(jadlerHandlerFile.toPath(), cuHandler.toString().getBytes());

        // Fix RequestUtils
        File requestUtilsFile = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/RequestUtils.java");
        CompilationUnit cuUtils = StaticJavaParser.parse(requestUtilsFile);
        cuUtils.getImports().forEach(i -> {
            if (i.getNameAsString().startsWith("javax.servlet")) {
                i.setName(i.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        });
        Files.write(requestUtilsFile.toPath(), cuUtils.toString().getBytes());

        // Fix JettyStubHttpServer
        File jettyServerFile = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        CompilationUnit cu = StaticJavaParser.parse(jettyServerFile);

        // Update imports
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        cu.addImport("org.eclipse.jetty.server.ServerConnector");
        cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
        cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");

        // Change field type
        cu.getClassByName("JettyStubHttpServer").ifPresent(cls -> {
            cls.getFieldByName("httpConnector").ifPresent(field -> {
                field.getVariable(0).setType("ServerConnector");
            });

            // Update constructor
            cls.getConstructors().stream().filter(c -> c.getParameters().size() == 1).findFirst().ifPresent(constructor -> {
                BlockStmt block = StaticJavaParser.parseBlock(
                    "{" +
                    "    this.server = new Server();" +
                    "    HttpConfiguration httpConfig = new HttpConfiguration();" +
                    "    httpConfig.setSendServerVersion(false);" +
                    "    httpConfig.setSendDateHeader(true);" +
                    "    this.httpConnector = new ServerConnector(this.server, new HttpConnectionFactory(httpConfig));" +
                    "    this.httpConnector.setPort(port);" +
                    "    server.addConnector(this.httpConnector);" +
                    "}"
                );
                constructor.setBody(block);
            });
            
            // update getPort (optional, but keep it correct)
            cls.getMethodsByName("getPort").forEach(m -> {
                m.setBody(StaticJavaParser.parseBlock("{ return this.httpConnector.getLocalPort(); }"));
            });
        });

        Files.write(jettyServerFile.toPath(), cu.toString().getBytes());
    }
}
