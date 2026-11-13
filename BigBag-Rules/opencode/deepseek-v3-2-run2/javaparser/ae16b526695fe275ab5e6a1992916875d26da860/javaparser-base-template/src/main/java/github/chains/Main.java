package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <source-dir> <target-dir>");
            System.err.println("Example: java Main /path/to/project /path/to/transformed");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        Path targetDir = Paths.get(args[1]);
        
        try {
            transformProject(sourceDir, targetDir);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir, Path targetDir) throws IOException {
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        JavaParser parser = new JavaParser();
            
        for (Path javaFile : javaFiles) {
            Path relativePath = sourceDir.relativize(javaFile);
            Path targetFile = targetDir.resolve(relativePath);
            
            Files.createDirectories(targetFile.getParent());
            
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
            JettyApiTransformer transformer = new JettyApiTransformer();
            cu.accept(transformer, null);
            
            Files.writeString(targetFile, cu.toString());
        }
    }
    
    private static class JettyApiTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            // Generic pattern: Replace old connector import with new connector import
            if (id.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                id.setName("org.eclipse.jetty.server.ServerConnector");
            }
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            // Generic pattern: Replace old connector constructor with new connector constructor
            if (expr.getType().asString().equals("SelectChannelConnector")) {
                expr.setType("ServerConnector");
                expr.getArguments().clear();
                // In Jetty 9.4.41, ServerConnector requires Server and ConnectionFactory
                // We'll add a minimal working constructor
                expr.addArgument(new NameExpr("server"));
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            String methodName = expr.getNameAsString();
            
            // Generic pattern: Remove deprecated method calls on Server
            // Old: server.setSendServerVersion(false), server.setSendDateHeader(true)
            // New: Configure via HttpConfiguration
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                if (expr.getScope().isPresent() && 
                    expr.getScope().get().toString().equals("server")) {
                    // Remove these calls - configuration moved to HttpConfiguration
                    return null;
                }
            }
            
            // Generic pattern: Remove setPort calls on connector
            // Old: httpConnector.setPort(port)
            // New: Port configured in constructor
            if (methodName.equals("setPort")) {
                if (expr.getScope().isPresent() && 
                    expr.getScope().get().toString().contains("Connector")) {
                    // Remove this call - port now configured differently
                    return null;
                }
            }
            
            return (Node) super.visit(expr, arg);
        }
    }
}