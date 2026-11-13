package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile);
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
             .filter(path -> path.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
        
        JettyMigrationVisitor visitor = new JettyMigrationVisitor();
        CompilationUnit modifiedCu = (CompilationUnit) cu.accept(visitor, null);
        
        if (visitor.wasModified()) {
            Files.writeString(filePath, modifiedCu.toString());
            return true;
        }
        return false;
    }
    
    private static class JettyMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Replace deprecated nio package import with ServerConnector
            if (n.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                modified = true;
                ImportDeclaration newImport = n.clone();
                newImport.setName("org.eclipse.jetty.server.ServerConnector");
                return newImport;
            }
            return n;
        }
        
        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            // Replace new SelectChannelConnector() with new ServerConnector(server)
            if (n.getType().asString().equals("SelectChannelConnector")) {
                modified = true;
                ObjectCreationExpr newExpr = n.clone();
                newExpr.setType("ServerConnector");
                // Add server as parameter
                newExpr.setArguments(NodeList.nodeList(
                    new com.github.javaparser.ast.expr.NameExpr("server")
                ));
                return newExpr;
            }
            return n;
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            // Remove deprecated Server methods
            if (n.getNameAsString().equals("setSendServerVersion") || 
                n.getNameAsString().equals("setSendDateHeader")) {
                modified = true;
                // Remove these method calls entirely
                return null;
            }
            
            return n;
        }
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            // Update AbstractHandler.handle() method signature
            if (n.getNameAsString().equals("handle") && 
                n.getParameters().size() == 4 &&
                n.getParameter(2).getTypeAsString().equals("HttpServletRequest") &&
                n.getParameter(3).getTypeAsString().equals("HttpServletResponse")) {
                
                // Check if this extends AbstractHandler
                if (n.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                    .map(cls -> cls.getExtendedTypes()
                        .stream()
                        .anyMatch(type -> type.getNameAsString().equals("AbstractHandler")))
                    .orElse(false)) {
                    
                    modified = true;
                    
                    // Update parameter types to use jakarta.servlet
                    n.getParameter(2).setType("jakarta.servlet.http.HttpServletRequest");
                    n.getParameter(3).setType("jakarta.servlet.http.HttpServletResponse");
                    
                    // Add jakarta.servlet imports if needed
                    n.findCompilationUnit().ifPresent(cu -> {
                        if (cu.getImports().stream().noneMatch(imp -> 
                            imp.getNameAsString().equals("jakarta.servlet.http.HttpServletRequest"))) {
                            cu.addImport("jakarta.servlet.http.HttpServletRequest");
                        }
                        if (cu.getImports().stream().noneMatch(imp -> 
                            imp.getNameAsString().equals("jakarta.servlet.http.HttpServletResponse"))) {
                            cu.addImport("jakarta.servlet.http.HttpServletResponse");
                        }
                    });
                }
            }
            return n;
        }
        
        @Override
        public Node visit(ExpressionStmt n, Void arg) {
            // Remove statements that became null after method removal
            if (n.getExpression() == null) {
                modified = true;
                return null;
            }
            return n;
        }
    }
}