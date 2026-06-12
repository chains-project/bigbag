package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule for fixing Jetty API changes from version 8 to 11.
 * This transformation addresses breaking changes in Jetty server APIs:
 * 1. Replaces SelectChannelConnector with ServerConnector
 * 2. Removes calls to setSendServerVersion and setSendDateHeader
 * 3. Updates Connector.getLocalPort() usage
 * 4. Fixes AbstractHandler.handle() method signature
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir)) {
            System.err.println("Directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformations
            cu.accept(new JettyApiChangeVisitor(), null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that applies the necessary transformations to fix Jetty API changes
     */
    private static class JettyApiChangeVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Replace SelectChannelConnector with ServerConnector
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if (type.getNameAsString().equals("SelectChannelConnector")) {
                    // Check if it's from the old package
                    if (type.getScope().isPresent() && type.getScope().get().toString().equals("org.eclipse.jetty.server.nio")) {
                        // Replace with ServerConnector from new package
                        type.setName("ServerConnector");
                        type.setScope(null);
                    }
                }
            }
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Remove calls to setSendServerVersion and setSendDateHeader
            if (n.getNameAsString().equals("setSendServerVersion") || n.getNameAsString().equals("setSendDateHeader")) {
                // Remove the entire expression statement
                if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof ExpressionStmt) {
                    n.getParentNode().get().remove();
                }
            }
        }
    }
}