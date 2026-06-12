package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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

public class ImprovedMain {
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
                // We'll remove this import
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
                
                // Add import for ServerConnector if not already present
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
        public Node visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Check if this class extends AbstractHandler
            if (n.getExtendedTypes().isNonEmpty()) {
                for (ClassOrInterfaceType extendedType : n.getExtendedTypes()) {
                    if (extendedType.getNameAsString().equals("AbstractHandler")) {
                        // Add import for jakarta.servlet if not already present
                        CompilationUnit cu = n.findCompilationUnit().orElse(null);
                        if (cu != null) {
                            boolean hasJakartaServletImport = cu.getImports().stream()
                                .anyMatch(imp -> imp.getNameAsString().startsWith("jakarta.servlet"));
                            
                            if (!hasJakartaServletImport) {
                                cu.addImport("jakarta.servlet.ServletException");
                                cu.addImport("jakarta.servlet.http.HttpServletRequest");
                                cu.addImport("jakarta.servlet.http.HttpServletResponse");
                                modified = true;
                            }
                        }
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            // Update handle method signature
            if (n.getNameAsString().equals("handle")) {
                // Check if this is a Handler subclass
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
            // These methods moved from Server to HttpConfiguration
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                // We need to create HttpConfiguration and apply settings to it
                BlockStmt block = n.findAncestor(BlockStmt.class).orElse(null);
                if (block != null) {
                    // Create HttpConfiguration at the beginning of the constructor
                    ClassOrInterfaceDeclaration classDecl = n.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
                    if (classDecl != null) {
                        // Find constructor
                        classDecl.getMethods().stream()
                            .filter(m -> m.isDefaultConstructor() || m.getNameAsString().equals(classDecl.getNameAsString()))
                            .findFirst()
                            .ifPresent(constructor -> {
                                BlockStmt constructorBody = constructor.getBody().orElse(null);
                                if (constructorBody != null) {
                                    // Check if HttpConfiguration already exists in this constructor
                                    boolean hasHttpConfig = constructorBody.getStatements().stream()
                                        .anyMatch(stmt -> stmt.toString().contains("HttpConfiguration"));
                                    
                                    if (!hasHttpConfig) {
                                        // Create HttpConfiguration config = new HttpConfiguration();
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
                                        
                                        // Insert at the beginning of constructor
                                        constructorBody.addStatement(0, configStmt);
                                        
                                        // Add import if needed
                                        CompilationUnit cu = n.findCompilationUnit().orElse(null);
                                        if (cu != null) {
                                            boolean hasImport = cu.getImports().stream()
                                                .anyMatch(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.HttpConfiguration"));
                                            
                                            if (!hasImport) {
                                                cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
                                            }
                                        }
                                        
                                        modified = true;
                                    }
                                    
                                    // Change method call to use httpConfig
                                    n.setScope(new NameExpr("httpConfig"));
                                    modified = true;
                                }
                            });
                    }
                }
            }
            
            // Transformation for connector.setPort() - this should work if we change the type to ServerConnector
            // which has setPort() method
            
            // Transformation for connector.getLocalPort() - this should work if we change the type to ServerConnector
            // which has getLocalPort() method
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().getNameAsString().equals("SelectChannelConnector")) {
                // Transform SelectChannelConnector to ServerConnector
                // new SelectChannelConnector() -> new ServerConnector(server)
                
                // Find the Server variable in the constructor
                ClassOrInterfaceDeclaration classDecl = n.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
                if (classDecl != null) {
                    // Look for Server variable
                    String serverVar = findServerVariable(classDecl);
                    
                    // Change constructor call
                    n.setType("ServerConnector");
                    n.getArguments().clear();
                    
                    if (serverVar != null) {
                        // Add server as first argument
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
            } else if (n.getType().getNameAsString().equals("Server")) {
                // Check if we need to add HttpConfiguration parameter
                BlockStmt block = n.findAncestor(BlockStmt.class).orElse(null);
                if (block != null) {
                    // Check if there are calls to setSendServerVersion or setSendDateHeader
                    boolean hasServerConfigMethods = block.findAll(MethodCallExpr.class).stream()
                        .anyMatch(mce -> {
                            String name = mce.getNameAsString();
                            return (name.equals("setSendServerVersion") || name.equals("setSendDateHeader")) &&
                                   mce.getScope().isPresent() && 
                                   mce.getScope().get().toString().equals("server");
                        });
                    
                    if (hasServerConfigMethods) {
                        // Server needs HttpConfiguration
                        n.getArguments().clear();
                        n.addArgument(new NameExpr("httpConfig"));
                        modified = true;
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        private String findServerVariable(ClassOrInterfaceDeclaration classDecl) {
            // Look for Server field declaration
            for (com.github.javaparser.ast.body.FieldDeclaration field : classDecl.getFields()) {
                if (field.getElementType().toString().equals("Server")) {
                    // Get the variable name
                    return field.getVariables().get(0).getNameAsString();
                }
            }
            return "server"; // Default variable name
        }
    }
}