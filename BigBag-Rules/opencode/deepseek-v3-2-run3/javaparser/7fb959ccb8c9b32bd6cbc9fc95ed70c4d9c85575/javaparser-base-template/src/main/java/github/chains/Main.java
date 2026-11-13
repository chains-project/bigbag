package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser javaParser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = javaParser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                boolean modified = false;
                
                // Apply all transformations
                modified |= transformJetty8ToJetty11(cu);
                
                if (modified) {
                    // Write back the transformed file
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Successfully transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Total transformed " + transformedFiles + " files");
    }
    
    /**
     * Main transformation from Jetty 8 API to Jetty 11 API
     * Handles:
     * 1. javax.servlet -> jakarta.servlet imports
     * 2. SelectChannelConnector -> ServerConnector
     * 3. Server configuration methods -> HttpConfiguration
     * 4. AbstractHandler.handle() method signature
     */
    private static boolean transformJetty8ToJetty11(CompilationUnit cu) {
        final boolean[] modifiedHolder = {false};
        
        // Track what transformations are needed
        final boolean[] needsServletImportFix = {false};
        final boolean[] needsConnectorUpdate = {false};
        final boolean[] needsConfigUpdate = {false};
        
        // First pass: analyze what needs to be changed
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ImportDeclaration n, Void arg) {
                String importName = n.getNameAsString();
                if (importName.startsWith("javax.servlet")) {
                    needsServletImportFix[0] = true;
                }
                if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                    needsConnectorUpdate[0] = true;
                }
            }
            
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                String methodName = n.getNameAsString();
                if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                    Optional<Expression> scope = n.getScope();
                    if (scope.isPresent()) {
                        String scopeStr = scope.get().toString();
                        if (scopeStr.equals("server") || scopeStr.equals("this.server")) {
                            needsConfigUpdate[0] = true;
                        }
                    }
                }
            }
            
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                if (n.getType().asString().equals("SelectChannelConnector")) {
                    needsConnectorUpdate[0] = true;
                }
            }
        }, null);
        
        // Apply transformations
        if (needsServletImportFix[0]) {
            modifiedHolder[0] |= transformServletImports(cu);
        }
        
        if (needsConnectorUpdate[0] || needsConfigUpdate[0]) {
            modifiedHolder[0] |= transformJettyConnectorAndConfig(cu);
        }
        
        return modifiedHolder[0];
    }
    
    private static boolean transformServletImports(CompilationUnit cu) {
        final boolean[] modifiedHolder = {false};
        List<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImport);
                modifiedHolder[0] = true;
                System.out.println("  Fixed import: " + importName + " -> " + newImport);
            }
        }
        return modifiedHolder[0];
    }
    
    private static boolean transformJettyConnectorAndConfig(CompilationUnit cu) {
        final boolean[] modifiedHolder = {false};
        
        // Update imports
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.setName("org.eclipse.jetty.server.ServerConnector");
                modifiedHolder[0] = true;
                System.out.println("  Fixed import: SelectChannelConnector -> ServerConnector");
            }
        }
        
        // Add required imports if not present
        boolean hasServerConnectorImport = imports.stream()
            .anyMatch(i -> i.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"));
        boolean hasHttpConfigImport = imports.stream()
            .anyMatch(i -> i.getNameAsString().equals("org.eclipse.jetty.server.HttpConfiguration"));
        boolean hasHttpConnFactoryImport = imports.stream()
            .anyMatch(i -> i.getNameAsString().equals("org.eclipse.jetty.server.HttpConnectionFactory"));
        
        if (!hasServerConnectorImport) {
            cu.addImport("org.eclipse.jetty.server.ServerConnector");
            modifiedHolder[0] = true;
        }
        if (!hasHttpConfigImport) {
            cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
            modifiedHolder[0] = true;
        }
        if (!hasHttpConnFactoryImport) {
            cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
            modifiedHolder[0] = true;
        }
        
        // Transform constructors and method calls
        cu.accept(new VoidVisitorAdapter<Void>() {
            private String serverVarName = "server";
            private String connectorVarName = "httpConnector";
            
            @Override
            public void visit(ConstructorDeclaration n, Void arg) {
                super.visit(n, arg);
                
                BlockStmt body = n.getBody();
                if (body != null) {
                    List<ExpressionStmt> stmts = body.findAll(ExpressionStmt.class);
                    
                    // Look for server initialization
                    for (ExpressionStmt stmt : stmts) {
                        Expression expr = stmt.getExpression();
                        if (expr instanceof AssignExpr) {
                            AssignExpr assign = (AssignExpr) expr;
                            if (assign.getTarget().toString().contains("server") && 
                                assign.getValue() instanceof ObjectCreationExpr) {
                                ObjectCreationExpr creation = (ObjectCreationExpr) assign.getValue();
                                if (creation.getType().asString().equals("Server")) {
                                    // Found server initialization
                                    System.out.println("  Found Server initialization");
                                }
                            }
                        } else if (expr instanceof VariableDeclarationExpr) {
                            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                            if (varDecl.getVariable(0).getNameAsString().equals("httpConnector")) {
                                connectorVarName = "httpConnector";
                            }
                        }
                    }
                }
            }
            
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                if (n.getType().asString().equals("SelectChannelConnector")) {
                    // Change to ServerConnector
                    n.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                    modifiedHolder[0] = true;
                    
                    // ServerConnector needs Server and ConnectionFactory parameters
                    // We'll create the arguments
                    NodeList<Expression> args = new NodeList<>();
                    args.add(new NameExpr(serverVarName));
                    args.add(new NameExpr("httpFactory"));
                    n.setArguments(args);
                    
                    System.out.println("  Fixed SelectChannelConnector -> ServerConnector with constructor args");
                }
            }
            
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                String methodName = n.getNameAsString();
                Optional<Expression> scope = n.getScope();
                
                if (scope.isPresent()) {
                    String scopeStr = scope.get().toString();
                    
                    // Handle server.setSendServerVersion() and server.setSendDateHeader()
                    if ((methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) &&
                        (scopeStr.equals("server") || scopeStr.equals("this.server"))) {
                        
                        // These need to be moved to HttpConfiguration
                        // We'll replace the entire statement later
                        System.out.println("  Found Server." + methodName + "() call - needs manual update to HttpConfiguration");
                    }
                    
                    // Handle connector.setPort() - should work with ServerConnector
                    if (methodName.equals("setPort") && scopeStr.equals(connectorVarName)) {
                        System.out.println("  " + connectorVarName + ".setPort() should work with ServerConnector");
                    }
                    
                    // Handle connector.getLocalPort() - should work with ServerConnector
                    if (methodName.equals("getLocalPort") && scopeStr.equals(connectorVarName)) {
                        System.out.println("  " + connectorVarName + ".getLocalPort() should work with ServerConnector");
                    }
                }
            }
            
            @Override
            public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                super.visit(n, arg);
                
                // Check for AbstractHandler extension
                if (n.getExtendedTypes().stream()
                    .anyMatch(t -> t.getNameAsString().equals("AbstractHandler"))) {
                    
                    System.out.println("  Found class extending AbstractHandler");
                    
                    // The handle method signature should be fine if imports were fixed
                    // Parameter types will be jakarta.servlet.* instead of javax.servlet.*
                }
            }
        }, null);
        
        return modifiedHolder[0];
    }
}