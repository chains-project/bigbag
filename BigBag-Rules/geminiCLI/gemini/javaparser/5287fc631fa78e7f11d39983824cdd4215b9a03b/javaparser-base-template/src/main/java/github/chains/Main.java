package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        replaceJavaxWithJakarta("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java");
        replaceJavaxWithJakarta("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/RequestUtils.java");
        fixJettyStubHttpServer("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
    }

    private static void replaceJavaxWithJakarta(String path) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(new File(path));
        for (ImportDeclaration id : cu.getImports()) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                id.setName(id.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        }
        Files.write(Paths.get(path), cu.toString().getBytes());
    }

    private static void fixJettyStubHttpServer(String path) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(new File(path));
        
        // Remove import org.eclipse.jetty.server.nio.SelectChannelConnector;
        cu.getImports().removeIf(id -> id.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        cu.getImports().removeIf(id -> id.getNameAsString().equals("org.eclipse.jetty.server.Connector"));
        
        // Add new imports
        cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
        cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
        cu.addImport("org.eclipse.jetty.server.ServerConnector");

        cu.findAll(FieldDeclaration.class).forEach(fd -> {
            if (fd.getVariable(0).getNameAsString().equals("httpConnector")) {
                fd.getVariable(0).setType("ServerConnector");
            }
        });

        // Find constructor JettyStubHttpServer(final int port)
        cu.findAll(ConstructorDeclaration.class).forEach(cd -> {
            if (cd.getParameters().size() == 1 && cd.getParameter(0).getNameAsString().equals("port")) {
                BlockStmt body = new BlockStmt();
                body.addStatement("this.server = new Server();");
                body.addStatement("HttpConfiguration httpConfig = new HttpConfiguration();");
                body.addStatement("httpConfig.setSendServerVersion(false);");
                body.addStatement("httpConfig.setSendDateHeader(true);");
                body.addStatement("ServerConnector connector = new ServerConnector(this.server, new HttpConnectionFactory(httpConfig));");
                body.addStatement("connector.setPort(port);");
                body.addStatement("this.httpConnector = connector;");
                body.addStatement("server.addConnector(this.httpConnector);");
                cd.setBody(body);
            }
        });

        Files.write(Paths.get(path), cu.toString().getBytes());
    }
}
