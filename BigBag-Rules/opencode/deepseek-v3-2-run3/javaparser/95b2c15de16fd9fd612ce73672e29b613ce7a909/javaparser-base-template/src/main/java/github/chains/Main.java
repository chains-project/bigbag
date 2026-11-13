package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Jetty API calls in: " + sourceDir);
        
        // Walk through all Java files
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
        
        System.out.println("Transformation complete.");
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            boolean modified = false;
            
            // Pattern 1: Replace javax.servlet imports with jakarta.servlet
            modified |= transformServletImports(cu);
            
            // Pattern 2: Replace SelectChannelConnector with ServerConnector and update constructor
            modified |= transformSelectChannelConnector(cu);
            
            // Pattern 3: Transform Server configuration to HttpConfiguration
            modified |= transformServerConfigurationMethods(cu);
            
            // Pattern 4: Update Connector variable type and method calls
            modified |= transformConnectorReferences(cu);
            
            // Pattern 5: Fix AbstractHandler.handle method signature
            modified |= transformHandlerMethodSignature(cu);
            
            if (modified) {
                // Write back the transformed file
                String transformedCode = cu.toString();
                Files.write(filePath, transformedCode.getBytes());
                System.out.println("Transformed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    private static boolean transformServletImports(CompilationUnit cu) {
        boolean modified = false;
        List<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                String newImportName = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImportName);
                modified = true;
            }
        }
        return modified;
    }
    
    private static boolean transformSelectChannelConnector(CompilationUnit cu) {
        boolean modified = false;
        
        // Find ObjectCreationExpr for SelectChannelConnector
        List<ObjectCreationExpr> objectCreations = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr creation : objectCreations) {
            if (creation.getType().toString().equals("SelectChannelConnector")) {
                // Change the type to ServerConnector
                creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                
                // Add ServerConnector constructor arguments (Server)
                // We need to find the Server variable in scope
                // For now, we'll add a placeholder and require manual fix
                creation.setArguments(new NodeList<>());
                
                modified = true;
                cu.addImport("org.eclipse.jetty.server.ServerConnector");
            }
        }
        
        // Also check for type references in variable declarations
        List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);
        for (ClassOrInterfaceType type : types) {
            if (type.getNameAsString().equals("SelectChannelConnector")) {
                type.setName("ServerConnector");
                modified = true;
                cu.addImport("org.eclipse.jetty.server.ServerConnector");
            }
        }
        
        // Remove old import if it exists
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            if (importDecl.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                cu.remove(importDecl);
                modified = true;
                break;
            }
        }
        
        return modified;
    }
    
    private static boolean transformServerConfigurationMethods(CompilationUnit cu) {
        boolean modified = false;
        
        // Look for Server object creation and configuration pattern
        List<ObjectCreationExpr> serverCreations = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr serverCreation : serverCreations) {
            if (serverCreation.getType().toString().equals("Server")) {
                // Find the parent block to add HttpConfiguration setup
                Optional<Node> parent = serverCreation.getParentNode();
                if (parent.isPresent() && parent.get() instanceof ExpressionStmt) {
                    Optional<Node> blockParent = parent.get().getParentNode();
                    if (blockParent.isPresent() && blockParent.get() instanceof BlockStmt) {
                        BlockStmt block = (BlockStmt) blockParent.get();
                        
                        // Find method calls on this server object in the same block
                        List<MethodCallExpr> methodCalls = block.findAll(MethodCallExpr.class);
                        List<MethodCallExpr> configCalls = new ArrayList<>();
                        
                        // Collect configuration method calls
                        for (MethodCallExpr methodCall : methodCalls) {
                            String methodName = methodCall.getNameAsString();
                            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                                configCalls.add(methodCall);
                            }
                        }
                        
                        // If we found configuration calls, we need to transform them
                        if (!configCalls.isEmpty()) {
                            // Create HttpConfiguration object
                            ObjectCreationExpr httpConfigCreation = new ObjectCreationExpr();
                            httpConfigCreation.setType(new ClassOrInterfaceType(null, "HttpConfiguration"));
                            
                            // Create HttpConnectionFactory
                            ObjectCreationExpr httpFactoryCreation = new ObjectCreationExpr();
                            httpFactoryCreation.setType(new ClassOrInterfaceType(null, "HttpConnectionFactory"));
                            httpFactoryCreation.setArguments(NodeList.nodeList(httpConfigCreation));
                            
                            // Replace configuration method calls
                            for (MethodCallExpr methodCall : configCalls) {
                                String methodName = methodCall.getNameAsString();
                                Expression arg = methodCall.getArgument(0);
                                
                                // Create new method call on httpConfig
                                MethodCallExpr newCall = new MethodCallExpr();
                                newCall.setScope(new NameExpr("httpConfig"));
                                newCall.setName(methodName);
                                newCall.setArguments(NodeList.nodeList(arg));
                                
                                // Replace the old method call
                                methodCall.replace(newCall);
                                modified = true;
                            }
                            
                            // Add HttpConfiguration import
                            cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
                            cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
                            
                            // Add variable declarations to block
                            VariableDeclarationExpr httpConfigVar = new VariableDeclarationExpr();
                            httpConfigVar.setVariable(0, new com.github.javaparser.ast.body.VariableDeclarator(
                                new ClassOrInterfaceType(null, "HttpConfiguration"), "httpConfig", httpConfigCreation));
                            
                            VariableDeclarationExpr httpFactoryVar = new VariableDeclarationExpr();
                            httpFactoryVar.setVariable(0, new com.github.javaparser.ast.body.VariableDeclarator(
                                new ClassOrInterfaceType(null, "HttpConnectionFactory"), "httpFactory", httpFactoryCreation));
                            
                            // Insert after server creation
                            int serverCreationIndex = block.getStatements().indexOf(parent.get());
                            if (serverCreationIndex >= 0) {
                                block.addStatement(serverCreationIndex + 1, new ExpressionStmt(httpConfigVar));
                                block.addStatement(serverCreationIndex + 2, new ExpressionStmt(httpFactoryVar));
                                modified = true;
                            }
                        }
                    }
                }
            }
        }
        
        return modified;
    }
    
    private static boolean transformConnectorReferences(CompilationUnit cu) {
        boolean modified = false;
        
        // Update variable declarations from Connector to ServerConnector
        List<com.github.javaparser.ast.body.VariableDeclarator> variables = 
            cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class);
        
        for (com.github.javaparser.ast.body.VariableDeclarator var : variables) {
            if (var.getType().toString().equals("Connector")) {
                // Check if this variable is initialized with ServerConnector
                Optional<Expression> initializer = var.getInitializer();
                if (initializer.isPresent() && initializer.get() instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) initializer.get();
                    if (creation.getType().toString().equals("ServerConnector")) {
                        // Change type to ServerConnector
                        var.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                        modified = true;
                    }
                }
            }
        }
        
        return modified;
    }
    
    private static boolean transformHandlerMethodSignature(CompilationUnit cu) {
        boolean modified = false;
        
        // Find classes that extend AbstractHandler
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration cls : classes) {
            if (cls.getExtendedTypes().stream()
                    .anyMatch(type -> type.getNameAsString().equals("AbstractHandler"))) {
                
                // Find handle method
                List<MethodDeclaration> methods = cls.getMethodsByName("handle");
                for (MethodDeclaration method : methods) {
                    // Check parameters for javax.servlet types
                    NodeList<Parameter> params = method.getParameters();
                    for (Parameter param : params) {
                        String paramType = param.getType().toString();
                        if (paramType.contains("javax.servlet")) {
                            // Replace with jakarta.servlet
                            String newType = paramType.replace("javax.servlet", "jakarta.servlet");
                            param.setType(newType);
                            modified = true;
                        }
                    }
                }
            }
        }
        
        return modified;
    }
}
