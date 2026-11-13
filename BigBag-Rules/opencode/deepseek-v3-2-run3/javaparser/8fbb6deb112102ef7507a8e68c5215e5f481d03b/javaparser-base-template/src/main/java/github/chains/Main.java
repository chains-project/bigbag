package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<Path> javaFiles = new ArrayList<>();
            Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(javaFiles::add);
            
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            JavaParser parser = new JavaParser();
            int filesModified = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
                    boolean modified = false;
                    
                    // Transformation 1: javax.servlet -> jakarta.servlet migration
                    modified |= migrateServletImports(cu);
                    
                    // Transformation 2: Remove deprecated Server methods
                    modified |= removeDeprecatedServerMethods(cu);
                    
                    // Transformation 3: SelectChannelConnector -> ServerConnector migration
                    modified |= migrateSelectChannelConnector(cu);
                    
                    if (modified) {
                        filesModified++;
                        Files.write(javaFile, cu.toString().getBytes());
                        System.out.println("Modified: " + javaFile);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                }
            }
            
            System.out.println("Total files modified: " + filesModified);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean migrateServletImports(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        // Replace javax.servlet imports with jakarta.servlet
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImport);
                modified[0] = true;
                System.out.println("  Replaced import: " + importName + " -> " + newImport);
            }
        }
        
        // Update type references in the code
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceType type, Void arg) {
                String typeName = type.getNameAsString();
                if (typeName.startsWith("javax.servlet.")) {
                    String newTypeName = typeName.replace("javax.servlet.", "jakarta.servlet.");
                    type.setName(newTypeName);
                    modified[0] = true;
                }
                return super.visit(type, arg);
            }
        }, null);
        
        return modified[0];
    }
    
    private static boolean removeDeprecatedServerMethods(CompilationUnit cu) {
        final boolean[] modified = {false};
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr methodCall, Void arg) {
                String methodName = methodCall.getNameAsString();
                
                // Remove calls to setSendServerVersion and setSendDateHeader on Server objects
                if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                    Optional<Node> parent = methodCall.getParentNode();
                    if (parent.isPresent() && parent.get() instanceof ExpressionStmt) {
                        // Remove the entire statement
                        parent.get().remove();
                        modified[0] = true;
                        System.out.println("  Removed deprecated method call: " + methodName);
                        return null; // Don't visit children of removed node
                    }
                }
                
                return super.visit(methodCall, arg);
            }
        }, null);
        
        return modified[0];
    }
    
    private static boolean migrateSelectChannelConnector(CompilationUnit cu) {
        final boolean[] modified = {false};
        final List<MethodCallExpr> portMethodCalls = new ArrayList<>();
        final List<MethodCallExpr> localPortMethodCalls = new ArrayList<>();
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ObjectCreationExpr expr, Void arg) {
                String typeName = expr.getTypeAsString();
                
                // Replace new SelectChannelConnector() with new ServerConnector(server)
                if (typeName.equals("SelectChannelConnector")) {
                    // Look for the server variable in the surrounding context
                    // For now, we'll transform to ServerConnector and let manual fixing handle constructor args
                    expr.setType("ServerConnector");
                    
                    // Add server as first argument if we can determine it's available
                    // This is a simplified transformation - actual fix may need more context
                    System.out.println("  WARNING: SelectChannelConnector -> ServerConnector transformation applied");
                    System.out.println("           Constructor arguments may need manual adjustment");
                    modified[0] = true;
                }
                
                return super.visit(expr, arg);
            }
            
            @Override
            public Visitable visit(MethodCallExpr methodCall, Void arg) {
                String methodName = methodCall.getNameAsString();
                
                // Collect setPort and getLocalPort calls for analysis
                if (methodName.equals("setPort")) {
                    portMethodCalls.add(methodCall);
                } else if (methodName.equals("getLocalPort")) {
                    localPortMethodCalls.add(methodCall);
                }
                
                return super.visit(methodCall, arg);
            }
        }, null);
        
        // Process port method calls
        for (MethodCallExpr methodCall : portMethodCalls) {
            System.out.println("  WARNING: setPort() method found - port should be passed to ServerConnector constructor");
            System.out.println("           Example transformation: connector.setPort(port) -> new ServerConnector(server, port)");
        }
        
        for (MethodCallExpr methodCall : localPortMethodCalls) {
            System.out.println("  WARNING: getLocalPort() method found - ServerConnector has getLocalPort() method");
        }
        
        // Add required imports
        boolean hasServerConnectorImport = cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"));
        
        if (!hasServerConnectorImport && modified[0]) {
            cu.addImport("org.eclipse.jetty.server.ServerConnector");
            modified[0] = true;
        }
        
        // Remove old import
        ImportDeclaration oldImport = cu.getImports().stream()
            .filter(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"))
            .findFirst()
            .orElse(null);
        
        if (oldImport != null) {
            oldImport.remove();
            modified[0] = true;
        }
        
        return modified[0];
    }
}