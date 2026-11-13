package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Transforms Jedis 3.x API calls to Jedis 4.x API");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Jedis 3.x to 4.x API in directory: " + sourceDir);
        
        int transformedFiles = 0;
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            for (Path javaFile : paths.filter(Files::isRegularFile)
                                      .filter(p -> p.toString().endsWith(".java"))
                                      .toArray(Path[]::new)) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformedFiles + " files.");
    }
    
    private static boolean transformFile(Path filePath) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + filePath)
        );
        
        boolean[] modified = new boolean[1];
        
        // First pass: update imports (Client -> Connection)
        cu.walk(com.github.javaparser.ast.ImportDeclaration.class, importDecl -> {
            if (importDecl.getNameAsString().equals("redis.clients.jedis.Client")) {
                importDecl.setName("redis.clients.jedis.Connection");
                modified[0] = true;
            }
        });
        
        // Second pass: transform constructors and method calls
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public com.github.javaparser.ast.Node visit(ConstructorDeclaration n, Void arg) {
                // Check if this is a Pipeline or Transaction constructor
                if (n.findAncestor(ClassOrInterfaceDeclaration.class)
                     .map(c -> c.getNameAsString().contains("Pipeline") || 
                               c.getNameAsString().contains("Transaction"))
                     .orElse(false)) {
                    
                    // Change Client parameters to Connection
                    for (Parameter param : n.getParameters()) {
                        String type = param.getTypeAsString();
                        if (type.equals("Client") || type.equals("redis.clients.jedis.Client")) {
                            param.setType("Connection");
                            modified[0] = true;
                        }
                    }
                }
                return (com.github.javaparser.ast.Node) super.visit(n, arg);
            }
            
            @Override
            public com.github.javaparser.ast.Node visit(MethodCallExpr n, Void arg) {
                // Change client.field to connection.field
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    if (scope instanceof NameExpr) {
                        NameExpr nameExpr = (NameExpr) scope;
                        if (nameExpr.getNameAsString().equals("client")) {
                            nameExpr.setName("connection");
                            modified[0] = true;
                        }
                    }
                }
                
                // Handle getResponse() calls - rename to appendCommand
                if (n.getNameAsString().equals("getResponse")) {
                    n.setName("appendCommand");
                    modified[0] = true;
                    // Note: This is a simplification - the actual transformation
                    // would need to wrap the builder in a CommandObject
                }
                
                return (com.github.javaparser.ast.Node) super.visit(n, arg);
            }
            
            @Override
            public com.github.javaparser.ast.Node visit(com.github.javaparser.ast.stmt.ExpressionStmt n, Void arg) {
                // Remove super.setClient(client) statements
                if (n.getExpression() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) n.getExpression();
                    if (methodCall.getNameAsString().equals("setClient") && 
                        methodCall.getScope().isPresent() &&
                        methodCall.getScope().get() instanceof com.github.javaparser.ast.expr.SuperExpr) {
                        // Remove this entire statement
                        modified[0] = true;
                        return null; // Remove the node
                    }
                }
                return (com.github.javaparser.ast.Node) super.visit(n, arg);
            }
        }, null);
        
        if (modified[0]) {
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
}