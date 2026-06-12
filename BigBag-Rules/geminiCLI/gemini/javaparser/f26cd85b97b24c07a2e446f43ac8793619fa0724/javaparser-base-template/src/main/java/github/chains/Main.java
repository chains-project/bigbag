package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        fixJadlerHandler();
        fixRequestUtils();
        fixJettyStubHttpServer();
    }

    private static void fixJadlerHandler() throws Exception {
        Path path = Paths.get("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java");
        CompilationUnit cu = StaticJavaParser.parse(path);
        
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration id : imports) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                String newName = id.getNameAsString().replace("javax.servlet", "jakarta.servlet");
                id.setName(newName);
            }
        }
        
        Files.write(path, cu.toString().getBytes());
    }

    private static void fixRequestUtils() throws Exception {
        Path path = Paths.get("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/RequestUtils.java");
        CompilationUnit cu = StaticJavaParser.parse(path);
        
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration id : imports) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                String newName = id.getNameAsString().replace("javax.servlet", "jakarta.servlet");
                id.setName(newName);
            }
        }
        
        Files.write(path, cu.toString().getBytes());
    }

    private static void fixJettyStubHttpServer() throws Exception {
        Path path = Paths.get("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        CompilationUnit cu = StaticJavaParser.parse(path);
        
        List<ImportDeclaration> imports = cu.getImports();
        imports.removeIf(id -> id.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        imports.removeIf(id -> id.getNameAsString().equals("org.eclipse.jetty.server.Connector"));
        
        cu.addImport("org.eclipse.jetty.server.ServerConnector");
        cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
        cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");

        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("JettyStubHttpServer").get();
        
        for (FieldDeclaration field : classDecl.getFields()) {
            if (field.getVariable(0).getNameAsString().equals("httpConnector")) {
                field.getVariable(0).setType("ServerConnector");
            }
        }

        for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
            if (constructor.getParameters().size() == 1 && constructor.getParameter(0).getNameAsString().equals("port")) {
                BlockStmt block = StaticJavaParser.parseBlock(
                    "{\n" +
                    "    this.server = new Server();\n" +
                    "    HttpConfiguration httpConfig = new HttpConfiguration();\n" +
                    "    httpConfig.setSendServerVersion(false);\n" +
                    "    httpConfig.setSendDateHeader(true);\n" +
                    "    this.httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));\n" +
                    "    this.httpConnector.setPort(port);\n" +
                    "    server.addConnector(this.httpConnector);\n" +
                    "}"
                );
                constructor.setBody(block);
            }
        }
        
        Files.write(path, cu.toString().getBytes());
    }
}
