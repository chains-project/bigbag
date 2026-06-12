package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        fixJadlerHandler();
        fixRequestUtils();
        fixJettyStubHttpServer();
    }

    private static void fixJadlerHandler() throws Exception {
        File file = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JadlerHandler.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                id.setName(id.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        }
        
        save(file, cu);
    }

    private static void fixRequestUtils() throws Exception {
        File file = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/RequestUtils.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        for (ImportDeclaration id : cu.findAll(ImportDeclaration.class)) {
            if (id.getNameAsString().startsWith("javax.servlet")) {
                id.setName(id.getNameAsString().replace("javax.servlet", "jakarta.servlet"));
            }
        }
        
        save(file, cu);
    }
    
    private static void fixJettyStubHttpServer() throws Exception {
        File file = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        List<ImportDeclaration> toRemove = cu.getImports().stream()
                .filter(id -> id.getNameAsString().equals("org.eclipse.jetty.server.Connector") || 
                              id.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"))
                .collect(Collectors.toList());
        toRemove.forEach(ImportDeclaration::remove);
        
        cu.addImport("org.eclipse.jetty.server.ServerConnector");
        cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
        cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
        
        ClassOrInterfaceDeclaration clazz = cu.getClassByName("JettyStubHttpServer").get();
        
        FieldDeclaration fd = clazz.getFieldByName("httpConnector").get();
        fd.getVariable(0).setType("ServerConnector");
        
        for (ConstructorDeclaration cd : clazz.getConstructors()) {
            if (cd.getParameters().size() == 1) {
                BlockStmt block = cd.getBody();
                
                block.getStatements().clear();
                
                block.addStatement(StaticJavaParser.parseStatement("this.server = new Server();"));
                block.addStatement(StaticJavaParser.parseStatement("HttpConfiguration httpConfig = new HttpConfiguration();"));
                block.addStatement(StaticJavaParser.parseStatement("httpConfig.setSendServerVersion(false);"));
                block.addStatement(StaticJavaParser.parseStatement("httpConfig.setSendDateHeader(true);"));
                block.addStatement(StaticJavaParser.parseStatement("this.httpConnector = new ServerConnector(this.server, new HttpConnectionFactory(httpConfig));"));
                block.addStatement(StaticJavaParser.parseStatement("this.httpConnector.setPort(port);"));
                block.addStatement(StaticJavaParser.parseStatement("server.addConnector(this.httpConnector);"));
            }
        }
        
        save(file, cu);
    }
    
    private static void save(File file, CompilationUnit cu) throws Exception {
        try (PrintWriter out = new PrintWriter(file)) {
            out.print(cu.toString());
        }
    }
}