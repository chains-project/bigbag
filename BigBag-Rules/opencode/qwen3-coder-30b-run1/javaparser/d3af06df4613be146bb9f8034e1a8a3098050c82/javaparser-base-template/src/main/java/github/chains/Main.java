package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        Path sourceDir = Paths.get(args.length > 0 ? args[0] : "/workspace/jadler");
        processDirectory(sourceDir);
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation rules for Jetty breaking changes
            new JettyApiChangeVisitor().visit(cu, null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes());
        } catch (IOException e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to transform Jetty API breaking changes
     */
    private static class JettyApiChangeVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(CompilationUnit cu, Void arg) {
            super.visit(cu, arg);
            
            // Fix imports: remove SelectChannelConnector import
            java.util.List<com.github.javaparser.ast.ImportDeclaration> importsCopy = new java.util.ArrayList<>(cu.getImports());
            importsCopy.removeIf(importDecl -> 
                importDecl.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
            cu.getImports().clear();
            cu.getImports().addAll(importsCopy);
            
            // Add ServerConnector import if needed
            if (!cu.getImports().stream().anyMatch(importDecl -> 
                importDecl.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"))) {
                cu.addImport("org.eclipse.jetty.server.ServerConnector");
            }
        }
        
        @Override
        public void visit(MethodCallExpr mce, Void arg) {
            super.visit(mce, arg);
            
            // Remove setSendServerVersion and setSendDateHeader method calls
            if (mce.getNameAsString().equals("setSendServerVersion") || 
                mce.getNameAsString().equals("setSendDateHeader")) {
                if (mce.getScope().isPresent()) {
                    // This is a method call on a server object
                    // Remove the entire statement
                    ExpressionStmt parent = (ExpressionStmt) mce.getParentNode().orElse(null);
                    if (parent != null) {
                        parent.remove();
                    }
                }
            }
        }
        
        @Override
        public void visit(VariableDeclarator vd, Void arg) {
            super.visit(vd, arg);
            
            // Change SelectChannelConnector to ServerConnector
            if (vd.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) vd.getType();
                if (type.getNameAsString().equals("SelectChannelConnector")) {
                    type.setName("ServerConnector");
                }
            }
        }
    }
}