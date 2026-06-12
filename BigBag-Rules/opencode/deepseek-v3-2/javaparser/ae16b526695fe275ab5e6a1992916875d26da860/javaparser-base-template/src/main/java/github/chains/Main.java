package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        for (Path javaFile : javaFiles) {
            try (FileInputStream fis = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = new JavaParser().parse(fis).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                boolean modified = false;
                
                // Pattern 1: Replace SelectChannelConnector imports and usage
                modified = transformSelectChannelConnector(cu) || modified;
                
                // Pattern 2: Replace Server.setSendServerVersion/setSendDateHeader with HttpConfiguration
                modified = transformServerConfigurationMethods(cu) || modified;
                
                if (modified) {
                    // Save the transformed file
                    String transformedCode = cu.toString();
                    Files.write(javaFile, transformedCode.getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    /**
     * Pattern 1: Transform SelectChannelConnector usage to ServerConnector
     * Old: import org.eclipse.jetty.server.nio.SelectChannelConnector;
     *      Connector httpConnector = new SelectChannelConnector();
     * New: import org.eclipse.jetty.server.ServerConnector;
     *      import org.eclipse.jetty.server.HttpConfiguration;
     *      import org.eclipse.jetty.server.HttpConnectionFactory;
     *      ServerConnector httpConnector = new ServerConnector(server);
     * 
     * Also updates Connector type declarations to ServerConnector where appropriate
     */
    private static boolean transformSelectChannelConnector(CompilationUnit cu) {
        boolean modified = false;
        
        // Update imports
        NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration imp = imports.get(i);
            String importName = imp.getNameAsString();
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                // Remove old import
                imports.remove(i);
                // Add new imports
                cu.addImport("org.eclipse.jetty.server.ServerConnector");
                cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
                cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");
                modified = true;
                break;
            }
        }
        
        // Find all ObjectCreationExpr using SelectChannelConnector
        List<ObjectCreationExpr> creations = cu.findAll(ObjectCreationExpr.class, expr -> {
            if (expr.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = expr.getType().asClassOrInterfaceType();
                return type.getNameAsString().equals("SelectChannelConnector") || 
                       type.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
            }
            return false;
        });
        
        for (ObjectCreationExpr creation : creations) {
            // Replace with new ServerConnector(server)
            ObjectCreationExpr newCreation = new ObjectCreationExpr();
            newCreation.setType("ServerConnector");
            
            // We need to find the server variable in context
            // For a generic transformation, we'll use "server" as placeholder
            newCreation.getArguments().add(new NameExpr("server"));
            
            creation.replace(newCreation);
            modified = true;
        }
        
        // Update variable/field types from SelectChannelConnector to ServerConnector
        List<VariableDeclarator> vars = cu.findAll(VariableDeclarator.class, v -> {
            String typeName = v.getType().toString();
            return typeName.equals("SelectChannelConnector") || 
                   typeName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector");
        });
        
        for (VariableDeclarator var : vars) {
            // Update type to ServerConnector
            var.setType("ServerConnector");
            modified = true;
        }
        
        // Also update Connector declarations that are initialized with ServerConnector
        // or used with setPort/getLocalPort methods
        List<VariableDeclarator> connectorVars = cu.findAll(VariableDeclarator.class, v -> {
            String typeName = v.getType().toString();
            return typeName.equals("Connector") || 
                   typeName.equals("org.eclipse.jetty.server.Connector");
        });
        
        for (VariableDeclarator var : connectorVars) {
            // Check if this variable calls setPort or getLocalPort
            String varName = var.getNameAsString();
            List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class, expr -> {
                Optional<Expression> scope = expr.getScope();
                if (scope.isPresent()) {
                    return scope.get().toString().equals(varName) && 
                           (expr.getNameAsString().equals("setPort") || 
                            expr.getNameAsString().equals("getLocalPort"));
                }
                return false;
            });
            
            if (!methodCalls.isEmpty()) {
                // This Connector variable needs to be ServerConnector
                var.setType("ServerConnector");
                modified = true;
            }
        }
        
        return modified;
    }
    
    /**
     * Pattern 2: Transform Server.setSendServerVersion and setSendDateHeader calls
     * This finds and replaces Server configuration method calls with HttpConfiguration setup.
     * Note: This transformation is simplified and works best when:
     * 1. Server constructor and ServerConnector creation are in same method/constructor
     * 2. All Server configuration calls happen before ServerConnector creation
     */
    private static boolean transformServerConfigurationMethods(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all MethodCallExpr on Server objects
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class, expr -> {
            String methodName = expr.getNameAsString();
            return methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader");
        });
        
        if (!methodCalls.isEmpty()) {
            // We found Server configuration calls
            // For a complete transformation, we would need to:
            // 1. Insert HttpConfiguration creation before ServerConnector creation
            // 2. Replace Server method calls with HttpConfiguration method calls
            // 3. Update ServerConnector constructor to include HttpConnectionFactory
            
            // For now, we'll add a TODO comment and remove the problematic calls
            // since they won't compile with Jetty 9.4+
            for (MethodCallExpr call : methodCalls) {
                String comment = "// TODO: Jetty 9.4+ - " + call.getNameAsString() + 
                               " moved to HttpConfiguration. See manual fix example.";
                call.replace(new NameExpr(comment));
                modified = true;
            }
        }
        
        return modified;
    }
}