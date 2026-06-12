package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Jetty 9/10 to Jetty 11 migration.
 * 
 * Breaking changes addressed:
 * 1. javax.servlet → jakarta.servlet (Jakarta EE 9+)
 * 2. SelectChannelConnector → ServerConnector (different package)
 * 3. Server.setSendServerVersion() removed → configure via HttpConfiguration
 * 4. Server.setSendDateHeader() removed → configure via HttpConfiguration  
 * 5. Connector.setPort() removed → port passed to ServerConnector constructor
 * 6. AbstractHandler.handle() signature changed to use jakarta.servlet
 * 
 * This is a generic rule that can be applied to any project migrating from
 * Jetty 9/10 to Jetty 11.
 */
public class Jetty11Transformation {
    
    private static class TransformationState {
        boolean modified = false;
        List<String> importsToAdd = new ArrayList<>();
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar jetty11-transformer.jar <source-directory>");
            System.err.println("Example: java -jar jetty11-transformer.jar /path/to/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("This transformation handles Jetty 9/10 to Jetty 11 breaking changes:");
        System.out.println("1. javax.servlet → jakarta.servlet");
        System.out.println("2. SelectChannelConnector → ServerConnector");
        System.out.println("3. Server configuration methods moved to HttpConfiguration");
        System.out.println("4. Constructor and method signature updates");
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("\nFound " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("\nTransformation complete!");
            System.out.println("Transformed " + transformedFiles + " files");
            System.out.println("\nNOTE: Some changes require manual review:");
            System.out.println("- ServerConnector constructor needs Server parameter");
            System.out.println("- setSendServerVersion()/setSendDateHeader() need HttpConfiguration setup");
            System.out.println("- setPort() calls need to be moved to ServerConnector constructor");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            cu = parser.parse(in).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                return false;
            }
        }
        
        TransformationState state = new TransformationState();
        
        // Track imports for removal/addition
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            // Mark javax.servlet imports for update
            if (importName.startsWith("javax.servlet")) {
                state.importsToRemove.add(importDecl);
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                state.importsToAdd.add(newImport);
                System.out.println("\n" + javaFile.getFileName() + ": Updating import " + importName + " → " + newImport);
            }
            
            // Mark SelectChannelConnector import for update
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                state.importsToRemove.add(importDecl);
                state.importsToAdd.add("org.eclipse.jetty.server.ServerConnector");
                System.out.println("\n" + javaFile.getFileName() + ": Updating import SelectChannelConnector → ServerConnector");
            }
        }
        
        // Apply import updates
        for (ImportDeclaration importToRemove : state.importsToRemove) {
            cu.remove(importToRemove);
            state.modified = true;
        }
        
        for (String importToAdd : state.importsToAdd) {
            if (!cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(importToAdd))) {
                cu.addImport(importToAdd);
                state.modified = true;
            }
        }
        
        // Transformation 1: Update SelectChannelConnector to ServerConnector in code
        cu.accept(new VoidVisitorAdapter<TransformationState>() {
            @Override
            public void visit(ClassOrInterfaceType type, TransformationState state) {
                super.visit(type, state);
                if (type.getNameAsString().equals("SelectChannelConnector")) {
                    type.setName("ServerConnector");
                    state.modified = true;
                    System.out.println(javaFile.getFileName() + ": Updated type SelectChannelConnector → ServerConnector");
                }
            }
            
            @Override
            public void visit(ObjectCreationExpr expr, TransformationState state) {
                super.visit(expr, state);
                if (expr.getType().getNameAsString().equals("SelectChannelConnector")) {
                    expr.getType().setName("ServerConnector");
                    state.modified = true;
                    System.out.println(javaFile.getFileName() + ": Updated constructor SelectChannelConnector → ServerConnector");
                    
                    // ServerConnector requires Server parameter
                    if (expr.getArguments().size() == 0) {
                        System.out.println(javaFile.getFileName() + ": WARNING: ServerConnector needs Server parameter");
                    }
                }
            }
        }, state);
        
        // Transformation 2: Find and annotate removed methods
        cu.accept(new VoidVisitorAdapter<TransformationState>() {
            @Override
            public void visit(MethodCallExpr expr, TransformationState state) {
                super.visit(expr, state);
                
                String methodName = expr.getNameAsString();
                Optional<Expression> scopeOpt = expr.getScope();
                
                // Check for Server.setSendServerVersion() 
                if (methodName.equals("setSendServerVersion") && scopeOpt.isPresent()) {
                    String scope = scopeOpt.get().toString();
                    if (scope.contains("server") || scope.contains("Server")) {
                        System.out.println(javaFile.getFileName() + ": WARNING: setSendServerVersion() removed in Jetty 11");
                        System.out.println("  Replace with: HttpConfiguration config = new HttpConfiguration();");
                        System.out.println("  config.setSendServerVersion(false);");
                        System.out.println("  ServerConnector connector = new ServerConnector(server, config);");
                    }
                }
                
                // Check for Server.setSendDateHeader()
                if (methodName.equals("setSendDateHeader") && scopeOpt.isPresent()) {
                    String scope = scopeOpt.get().toString();
                    if (scope.contains("server") || scope.contains("Server")) {
                        System.out.println(javaFile.getFileName() + ": WARNING: setSendDateHeader() removed in Jetty 11");
                        System.out.println("  Replace with: HttpConfiguration config = new HttpConfiguration();");
                        System.out.println("  config.setSendDateHeader(true);");
                        System.out.println("  ServerConnector connector = new ServerConnector(server, config);");
                    }
                }
                
                // Check for Connector.setPort()
                if (methodName.equals("setPort") && scopeOpt.isPresent()) {
                    String scope = scopeOpt.get().toString();
                    if (scope.contains("connector") || scope.contains("Connector") || 
                        scope.contains("httpConnector")) {
                        System.out.println(javaFile.getFileName() + ": WARNING: setPort() removed in Jetty 11");
                        System.out.println("  Pass port to ServerConnector constructor instead:");
                        System.out.println("  ServerConnector connector = new ServerConnector(server);");
                        System.out.println("  connector.setPort(port);  // REMOVE THIS LINE");
                        System.out.println("  // Change to: ServerConnector connector = new ServerConnector(server);");
                        System.out.println("  // ServerConnector doesn't have setPort(), port is in constructor");
                    }
                }
            }
        }, state);
        
        // Transformation 3: Update AbstractHandler.handle() method signatures
        cu.accept(new VoidVisitorAdapter<TransformationState>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration classDecl, TransformationState state) {
                super.visit(classDecl, state);
                
                // Check if this class extends AbstractHandler
                boolean extendsAbstractHandler = classDecl.getExtendedTypes().stream()
                    .anyMatch(type -> type.getNameAsString().equals("AbstractHandler"));
                
                if (extendsAbstractHandler) {
                    for (MethodDeclaration method : classDecl.getMethods()) {
                        if (method.getNameAsString().equals("handle")) {
                            // Update parameter types
                            boolean updatedParams = false;
                            for (Parameter param : method.getParameters()) {
                                Type paramType = param.getType();
                                String typeStr = paramType.asString();
                                if (typeStr.contains("javax.servlet")) {
                                    String newType = typeStr.replace("javax.servlet", "jakarta.servlet");
                                    param.setType(newType);
                                    updatedParams = true;
                                }
                            }
                            if (updatedParams) {
                                state.modified = true;
                                System.out.println(javaFile.getFileName() + ": Updated handle() method parameters to jakarta.servlet");
                            }
                        }
                    }
                }
            }
        }, state);
        
        // Transformation 4: Look for constructors that create ServerConnector
        cu.accept(new VoidVisitorAdapter<TransformationState>() {
            @Override
            public void visit(ConstructorDeclaration constructor, TransformationState state) {
                super.visit(constructor, state);
                
                BlockStmt body = constructor.getBody();
                if (body != null) {
                    // Pattern: this.httpConnector = new SelectChannelConnector();
                    // Followed by: this.httpConnector.setPort(port);
                    List<ExpressionStmt> statements = body.findAll(ExpressionStmt.class);
                    for (int i = 0; i < statements.size(); i++) {
                        ExpressionStmt stmt = statements.get(i);
                        Expression expr = stmt.getExpression();
                        
                        // Look for ServerConnector creation
                        if (expr instanceof ObjectCreationExpr) {
                            ObjectCreationExpr creation = (ObjectCreationExpr) expr;
                            if (creation.getType().getNameAsString().equals("ServerConnector")) {
                                // Check next statements for setPort()
                                for (int j = i + 1; j < Math.min(i + 3, statements.size()); j++) {
                                    ExpressionStmt nextStmt = statements.get(j);
                                    if (nextStmt.getExpression() instanceof MethodCallExpr) {
                                        MethodCallExpr call = (MethodCallExpr) nextStmt.getExpression();
                                        if (call.getNameAsString().equals("setPort")) {
                                            System.out.println(javaFile.getFileName() + ": FOUND: ServerConnector creation with setPort() call");
                                            System.out.println("  Need to merge setPort() into constructor:");
                                            System.out.println("  new ServerConnector(server, port)");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }, state);
        
        if (state.modified) {
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
            return true;
        }
        
        return false;
    }
}