package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
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

/**
 * Generic transformation for migrating from Jetty 8/9 to Jetty 11.
 * This transformation handles the following breaking changes:
 * 
 * 1. javax.servlet -> jakarta.servlet package migration
 * 2. SelectChannelConnector -> ServerConnector replacement
 * 3. Server.setSendServerVersion()/setSendDateHeader() -> HttpConfiguration methods
 * 4. Connector type to ServerConnector type for methods like setPort() and getLocalPort()
 * 5. Adds necessary imports (HttpConfiguration, HttpConnectionFactory)
 */
public class JettyMigrationTransformation {
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
                
                // Apply comprehensive Jetty migration transformation
                JettyMigrationVisitor visitor = new JettyMigrationVisitor();
                cu.accept(visitor, null);
                if (visitor.isModified()) {
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
    
    static class JettyMigrationVisitor extends ModifierVisitor<Void> {
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
                return null; // Remove the import
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String typeName = n.getNameAsString();
            
            // Update SelectChannelConnector type references to ServerConnector
            if (typeName.equals("SelectChannelConnector")) {
                n.setName("ServerConnector");
                modified = true;
                
                // Add import if needed
                CompilationUnit cu = n.findCompilationUnit().orElse(null);
                if (cu != null) {
                    addImportIfMissing(cu, "org.eclipse.jetty.server.ServerConnector");
                }
            }
            
            // Update Connector type to ServerConnector in field/variable declarations
            // when we detect it's used with setPort() or getLocalPort()
            if (typeName.equals("Connector")) {
                // Check if this type is used in a context where we need ServerConnector
                Node parent = n.getParentNode().orElse(null);
                if (parent instanceof VariableDeclarator) {
                    VariableDeclarator varDecl = (VariableDeclarator) parent;
                    String varName = varDecl.getNameAsString();
                    
                    // Check if this variable is used with setPort() or getLocalPort()
                    CompilationUnit cu = n.findCompilationUnit().orElse(null);
                    if (cu != null) {
                        boolean usesPortMethods = cu.findAll(MethodCallExpr.class).stream()
                            .anyMatch(mce -> {
                                String methodName = mce.getNameAsString();
                                if (!(methodName.equals("setPort") || methodName.equals("getLocalPort"))) {
                                    return false;
                                }
                                return mce.getScope().isPresent() && 
                                       mce.getScope().get().toString().equals(varName);
                            });
                        
                        if (usesPortMethods) {
                            n.setName("ServerConnector");
                            modified = true;
                            addImportIfMissing(cu, "org.eclipse.jetty.server.ServerConnector");
                        }
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            // Update handle method signature in AbstractHandler subclasses
            if (n.getNameAsString().equals("handle")) {
                ClassOrInterfaceDeclaration classDecl = n.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
                if (classDecl != null && classDecl.getExtendedTypes().isNonEmpty()) {
                    for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                        if (extendedType.getNameAsString().equals("AbstractHandler")) {
                            // Update parameter types from javax.servlet to jakarta.servlet
                            n.getParameters().forEach(param -> {
                                String type = param.getTypeAsString();
                                if (type.startsWith("javax.servlet")) {
                                    param.setType(type.replace("javax.servlet", "jakarta.servlet"));
                                    modified = true;
                                }
                            });
                            
                            // Add jakarta.servlet imports if needed
                            CompilationUnit cu = n.findCompilationUnit().orElse(null);
                            if (cu != null) {
                                addImportIfMissing(cu, "jakarta.servlet.ServletException");
                                addImportIfMissing(cu, "jakarta.servlet.http.HttpServletRequest");
                                addImportIfMissing(cu, "jakarta.servlet.http.HttpServletResponse");
                            }
                        }
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            // Transformation for Server.setSendServerVersion() and Server.setSendDateHeader()
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                if (n.getScope().isPresent() && n.getScope().get().toString().equals("server")) {
                    BlockStmt block = n.findAncestor(BlockStmt.class).orElse(null);
                    if (block != null) {
                        // Create HttpConfiguration if not already present
                        ensureHttpConfigurationExists(block, n);
                        
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
            String typeName = n.getType().getNameAsString();
            
            if (typeName.equals("SelectChannelConnector")) {
                // Transform: new SelectChannelConnector() -> new ServerConnector(server, new HttpConnectionFactory(httpConfig))
                transformSelectChannelConnector(n);
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(FieldDeclaration n, Void arg) {
            // Update field types from Connector to ServerConnector if needed
            if (n.getElementType().toString().equals("Connector")) {
                CompilationUnit cu = n.findCompilationUnit().orElse(null);
                if (cu != null) {
                    for (VariableDeclarator var : n.getVariables()) {
                        String varName = var.getNameAsString();
                        
                        // Check if this field is used with setPort() or getLocalPort()
                        boolean usesPortMethods = cu.findAll(MethodCallExpr.class).stream()
                            .anyMatch(mce -> {
                                String methodName = mce.getNameAsString();
                                if (!(methodName.equals("setPort") || methodName.equals("getLocalPort"))) {
                                    return false;
                                }
                                return mce.getScope().isPresent() && 
                                       mce.getScope().get().toString().equals(varName);
                            });
                        
                        if (usesPortMethods) {
                            // Need to rebuild the field declaration with new type
                            // For simplicity, we'll skip this for now as it's complex
                            // The type will be updated when visiting ClassOrInterfaceType
                        }
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        private void transformSelectChannelConnector(ObjectCreationExpr n) {
            CompilationUnit cu = n.findCompilationUnit().orElse(null);
            if (cu == null) return;
            
            // Find the Server variable in the current class
            String serverVar = findServerVariable(n);
            
            // Ensure HttpConfiguration exists
            BlockStmt block = n.findAncestor(BlockStmt.class).orElse(null);
            if (block != null) {
                ensureHttpConfigurationExists(block, n);
            }
            
            // Change to: new ServerConnector(server, new HttpConnectionFactory(httpConfig))
            n.setType("ServerConnector");
            n.getArguments().clear();
            
            if (serverVar != null) {
                // Add server as first argument
                n.addArgument(new NameExpr(serverVar));
                
                // Create HttpConnectionFactory with httpConfig
                ObjectCreationExpr connectionFactory = new ObjectCreationExpr();
                connectionFactory.setType("HttpConnectionFactory");
                connectionFactory.addArgument(new NameExpr("httpConfig"));
                
                n.addArgument(connectionFactory);
                
                // Add imports
                addImportIfMissing(cu, "org.eclipse.jetty.server.ServerConnector");
                addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConnectionFactory");
                
                modified = true;
            }
        }
        
        private void ensureHttpConfigurationExists(BlockStmt block, Node context) {
            // Check if httpConfig already exists in this block
            boolean hasHttpConfig = block.getStatements().stream()
                .anyMatch(stmt -> stmt.toString().contains("HttpConfiguration httpConfig"));
            
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
                CompilationUnit cu = context.findCompilationUnit().orElse(null);
                if (cu != null) {
                    addImportIfMissing(cu, "org.eclipse.jetty.server.HttpConfiguration");
                }
                
                modified = true;
            }
        }
        
        private String findServerVariable(Node context) {
            ClassOrInterfaceDeclaration classDecl = context.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
            if (classDecl == null) return "server";
            
            // Look for Server field declaration
            for (FieldDeclaration field : classDecl.getFields()) {
                if (field.getElementType().toString().equals("Server")) {
                    return field.getVariables().get(0).getNameAsString();
                }
            }
            
            return "server"; // Default
        }
        
        private void addImportIfMissing(CompilationUnit cu, String importName) {
            boolean hasImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(importName));
            
            if (!hasImport) {
                cu.addImport(importName);
                modified = true;
            }
        }
    }
}