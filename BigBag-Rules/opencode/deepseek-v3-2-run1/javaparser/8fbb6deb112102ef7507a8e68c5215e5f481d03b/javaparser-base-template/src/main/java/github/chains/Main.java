package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                boolean modified = false;
                
                // Apply transformation 1: javax.servlet -> jakarta.servlet imports
                for (ImportDeclaration importDecl : cu.getImports()) {
                    String importName = importDecl.getNameAsString();
                    if (importName.startsWith("javax.servlet")) {
                        importDecl.setName(importName.replace("javax.servlet", "jakarta.servlet"));
                        modified = true;
                    }
                }
                
                // Apply transformation 2: Update imports for Jetty classes
                JettyImportVisitor importVisitor = new JettyImportVisitor();
                cu.accept(importVisitor, null);
                if (importVisitor.isModified()) {
                    modified = true;
                }
                
                // Apply transformation 3: Update method calls and object creations
                JettyApiVisitor apiVisitor = new JettyApiVisitor();
                cu.accept(apiVisitor, null);
                if (apiVisitor.isModified()) {
                    modified = true;
                }
                
                // Apply transformation 4: Update Handler method signature
                HandlerMethodVisitor handlerVisitor = new HandlerMethodVisitor();
                cu.accept(handlerVisitor, null);
                if (handlerVisitor.isModified()) {
                    modified = true;
                }
                
                if (modified) {
                    try (FileOutputStream out = new FileOutputStream(javaFile.toFile())) {
                        out.write(cu.toString().getBytes());
                        System.out.println("  Modified: " + javaFile);
                    }
                }
            }
        }
        
        System.out.println("Transformation complete!");
    }
    
    static class JettyImportVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            String importName = n.getNameAsString();
            
            // Replace javax.servlet with jakarta.servlet
            if (importName.startsWith("javax.servlet")) {
                n.setName(importName.replace("javax.servlet", "jakarta.servlet"));
                modified = true;
            }
            
            // Remove old nio package import
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                // We'll remove this import and add ServerConnector instead
                return null; // Remove the import
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            // Update type references in code
            String typeName = n.getNameAsString();
            
            if (typeName.equals("SelectChannelConnector")) {
                n.setName("ServerConnector");
                modified = true;
                
                // Check if we need to add import for ServerConnector
                CompilationUnit cu = n.findCompilationUnit().orElse(null);
                if (cu != null) {
                    boolean hasServerConnectorImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"));
                    
                    if (!hasServerConnectorImport) {
                        cu.addImport("org.eclipse.jetty.server.ServerConnector");
                        modified = true;
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
    }
    
    static class JettyApiVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            // Transformation for Server.setSendServerVersion() and Server.setSendDateHeader()
            // These methods moved from Server to HttpConfiguration
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                // We need to find or create an HttpConfiguration and call the method on it
                // This requires more complex transformation that depends on the context
                // For a generic rule, we'll transform server.method() to config.method()
                // where config is an HttpConfiguration variable that needs to be available
                
                // Check if scope is "server" (common pattern)
                if (n.getScope().isPresent() && n.getScope().get().toString().equals("server")) {
                    // Try to find or create HttpConfiguration in the current context
                    BlockStmt block = n.findAncestor(BlockStmt.class).orElse(null);
                    if (block != null) {
                        // Look for HttpConfiguration variable in the block
                        boolean hasHttpConfig = false;
                        for (com.github.javaparser.ast.stmt.Statement stmt : block.getStatements()) {
                            if (stmt.toString().contains("HttpConfiguration")) {
                                hasHttpConfig = true;
                                break;
                            }
                        }
                        
                        if (!hasHttpConfig) {
                            // Create HttpConfiguration at the beginning of the block
                            ObjectCreationExpr configCreation = new ObjectCreationExpr();
                            configCreation.setType("HttpConfiguration");
                            
                            ExpressionStmt configStmt = new ExpressionStmt(
                                new com.github.javaparser.ast.expr.VariableDeclarationExpr(
                                    new com.github.javaparser.ast.body.VariableDeclarator(
                                        new com.github.javaparser.ast.type.ClassOrInterfaceType("HttpConfiguration"),
                                        "httpConfig",
                                        configCreation
                                    )
                                )
                            );
                            
                            // Insert at the beginning of the block
                            block.addStatement(0, configStmt);
                            
                            // Add import if needed
                            CompilationUnit cu = n.findCompilationUnit().orElse(null);
                            if (cu != null) {
                                boolean hasImport = cu.getImports().stream()
                                    .anyMatch(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.HttpConfiguration"));
                                
                                if (!hasImport) {
                                    cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
                                }
                            }
                        }
                        
                        // Change method call to use httpConfig
                        n.setScope(new NameExpr("httpConfig"));
                        modified = true;
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().getNameAsString().equals("SelectChannelConnector")) {
                // Transform SelectChannelConnector to ServerConnector
                // SelectChannelConnector() -> new ServerConnector(server)
                
                // We need to find the Server variable in context
                BlockStmt block = n.findAncestor(BlockStmt.class).orElse(null);
                if (block != null) {
                    // Look for Server variable
                    String serverVar = "server";
                    
                    // Change constructor call
                    n.setType("ServerConnector");
                    n.getArguments().clear();
                    n.addArgument(new NameExpr(serverVar));
                    
                    // Add import if needed
                    CompilationUnit cu = n.findCompilationUnit().orElse(null);
                    if (cu != null) {
                        boolean hasImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"));
                        
                        if (!hasImport) {
                            cu.addImport("org.eclipse.jetty.server.ServerConnector");
                        }
                    }
                    
                    modified = true;
                }
            }
            
            return (Node) super.visit(n, arg);
        }
    }
    
    static class HandlerMethodVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            // Check if this is a handle method in a Handler subclass
            if (n.getNameAsString().equals("handle")) {
                // Check parameters
                if (n.getParameters().size() >= 4) {
                    // Update parameter types from javax.servlet to jakarta.servlet
                    n.getParameters().forEach(param -> {
                        String type = param.getTypeAsString();
                        if (type.startsWith("javax.servlet")) {
                            param.setType(type.replace("javax.servlet", "jakarta.servlet"));
                            modified = true;
                        }
                    });
                }
            }
            
            return (Node) super.visit(n, arg);
        }
    }
}