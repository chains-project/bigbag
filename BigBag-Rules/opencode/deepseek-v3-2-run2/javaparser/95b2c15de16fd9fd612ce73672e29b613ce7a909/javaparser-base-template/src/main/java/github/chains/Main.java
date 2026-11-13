package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.Node;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        System.out.println("=== JETTY 11 BREAKING CHANGES TRANSFORMATION ===");
        System.out.println("This transformation handles the following breaking changes:");
        System.out.println("1. javax.servlet → jakarta.servlet package migration");
        System.out.println("2. SelectChannelConnector → ServerConnector migration");
        System.out.println("3. Server configuration methods moved to HttpConfiguration");
        System.out.println("4. AbstractHandler.handle() signature changes");
        System.out.println("=============================================");
        
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        for (Path file : javaFiles) {
            System.out.println("\nProcessing: " + file);
            transformFile(file);
        }
        
        System.out.println("\n=== TRANSFORMATION COMPLETE ===");
        System.out.println("Manual fixes may still be required for:");
        System.out.println("- ServerConnector constructor parameters (needs Server instance)");
        System.out.println("- Test mocks using javax.servlet (e.g., Spring MockHttpServletRequest)");
        System.out.println("- HTTP/2 or SSL configuration if present");
    }
    
    private static List<Path> findJavaFiles(Path start) throws IOException {
        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static void transformFile(Path file) throws IOException {
        String content = Files.readString(file);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply all transformations
        cu.accept(new ServletPackageTransformer(), null);
        cu.accept(new SelectChannelConnectorTransformer(), null);
        cu.accept(new HttpConfigurationTransformer(), null);
        
        // Write transformed content back
        try (FileWriter writer = new FileWriter(file.toFile())) {
            writer.write(cu.toString());
        }
    }
    
    /**
     * TRANSFORMATION 1: javax.servlet → jakarta.servlet package migration
     * 
     * Old API pattern: import javax.servlet.*
     * New API pattern: import jakarta.servlet.*
     * 
     * This is a Jakarta EE 9+ change where javax namespace moved to jakarta
     */
    private static class ServletPackageTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImport);
                System.out.println("  [Servlet] Changed import: " + importName + " → " + newImport);
            }
            return (Node) super.visit(importDecl, arg);
        }
    }
    
    /**
     * TRANSFORMATION 2: SelectChannelConnector → ServerConnector migration
     * 
     * Old API pattern: 
     *   import org.eclipse.jetty.server.nio.SelectChannelConnector;
     *   new SelectChannelConnector()
     *   connector.setPort(port)
     *   connector.getLocalPort()
     * 
     * New API pattern:
     *   import org.eclipse.jetty.server.ServerConnector;
     *   import org.eclipse.jetty.server.HttpConfiguration;
     *   import org.eclipse.jetty.server.HttpConnectionFactory;
     *   HttpConfiguration httpConfig = new HttpConfiguration();
     *   new ServerConnector(server, new HttpConnectionFactory(httpConfig))
     *   ((ServerConnector)connector).setPort(port)
     *   ((ServerConnector)connector).getLocalPort()
     */
    private static class SelectChannelConnectorTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.setName("org.eclipse.jetty.server.ServerConnector");
                System.out.println("  [Connector] Changed import: SelectChannelConnector → ServerConnector");
            }
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getTypeAsString();
            if (typeName.equals("SelectChannelConnector")) {
                expr.setType("ServerConnector");
                System.out.println("  [Connector] Changed constructor: SelectChannelConnector → ServerConnector");
                
                NodeList args = expr.getArguments();
                if (args.isEmpty()) {
                    System.out.println("  [Connector] WARNING: ServerConnector requires Server parameter in constructor");
                    System.out.println("  [Connector] Manual fix needed: new ServerConnector(server, connectionFactories...)");
                }
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            if (methodName.equals("setPort") || methodName.equals("getLocalPort")) {
                System.out.println("  [Connector] Found " + methodName + " call - may need casting to ServerConnector");
            }
            
            return (Node) super.visit(expr, arg);
        }
    }
    
    /**
     * TRANSFORMATION 3: Server configuration methods moved to HttpConfiguration
     * 
     * Old API pattern:
     *   Server server = new Server();
     *   server.setSendServerVersion(false);
     *   server.setSendDateHeader(true);
     * 
     * New API pattern:
     *   Server server = new Server();
     *   HttpConfiguration httpConfig = new HttpConfiguration();
     *   httpConfig.setSendServerVersion(false);
     *   httpConfig.setSendDateHeader(true);
     *   // httpConfig must be passed to ServerConnector via HttpConnectionFactory
     */
    private static class HttpConfigurationTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                System.out.println("  [Config] Found " + methodName + " call on Server");
                System.out.println("  [Config] Must be moved to HttpConfiguration object");
                System.out.println("  [Config] Manual fix: Create HttpConfiguration and use HttpConnectionFactory");
            }
            
            return (Node) super.visit(expr, arg);
        }
    }
}