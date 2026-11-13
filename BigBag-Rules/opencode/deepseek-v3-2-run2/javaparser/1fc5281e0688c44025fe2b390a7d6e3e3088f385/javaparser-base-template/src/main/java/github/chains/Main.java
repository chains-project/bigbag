package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic transformation rule for Jetty 11 breaking API changes.
 * This transformation handles:
 * 1. Replace SelectChannelConnector with ServerConnector
 * 2. Move server configuration from Server to HttpConfiguration
 * 3. Update javax.servlet imports to jakarta.servlet
 * 4. Fix Handler method signatures
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            boolean modified = false;
            
            // Apply transformations
            modified |= updateImports(cu);
            modified |= replaceSelectChannelConnector(cu);
            modified |= updateServerConfiguration(cu);
            modified |= updateHandlerMethodSignatures(cu);
            
            if (modified) {
                String transformedContent = cu.toString();
                Files.writeString(filePath, transformedContent);
                System.out.println("Transformed: " + filePath);
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Update javax.servlet imports to jakarta.servlet
     */
    private static boolean updateImports(CompilationUnit cu) {
        boolean modified = false;
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImport);
                modified = true;
            }
        }
        return modified;
    }
    
    /**
     * Replace SelectChannelConnector with ServerConnector
     */
    private static boolean replaceSelectChannelConnector(CompilationUnit cu) {
        boolean[] modified = {false};
        
        // Update imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                importDecl.setName("org.eclipse.jetty.server.ServerConnector");
                modified[0] = true;
            }
        }
        
        // Update object creation expressions
        cu.findAll(ObjectCreationExpr.class).forEach(objectCreation -> {
            if (objectCreation.getType().asString().equals("SelectChannelConnector")) {
                objectCreation.setType("ServerConnector");
                modified[0] = true;
                
                // ServerConnector constructor requires Server parameter
                // Find the enclosing method/class to get the server variable
                Node parent = objectCreation.getParentNode().orElse(null);
                if (parent instanceof BlockStmt) {
                    // Look for server variable assignment in the same block
                    BlockStmt block = (BlockStmt) parent;
                    block.getStatements().stream()
                        .filter(stmt -> stmt.toString().contains("new Server()"))
                        .findFirst()
                        .ifPresent(serverStmt -> {
                            // Extract server variable name
                            String serverVar = extractServerVariable(serverStmt.toString());
                            if (serverVar != null) {
                                // Update ServerConnector constructor to include server parameter
                                objectCreation.getArguments().add(0, StaticJavaParser.parseExpression(serverVar));
                            }
                        });
                }
            }
        });
        
        // Update type references
        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            if (type.getNameAsString().equals("SelectChannelConnector")) {
                type.setName("ServerConnector");
                modified[0] = true;
            }
        });
        
        return modified[0];
    }
    
    /**
     * Extract server variable name from assignment statement
     */
    private static String extractServerVariable(String statement) {
        // Simple extraction - looks for patterns like "Server server = new Server();"
        if (statement.contains("new Server()")) {
            String[] parts = statement.split("\\s*=\\s*");
            if (parts.length > 0) {
                String[] varParts = parts[0].trim().split("\\s+");
                return varParts[varParts.length - 1]; // Last token is variable name
            }
        }
        return null;
    }
    
    /**
     * Move server configuration from Server to HttpConfiguration
     */
    private static boolean updateServerConfiguration(CompilationUnit cu) {
        boolean[] modified = {false};
        
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                // These methods are now on HttpConfiguration, not Server
                // We need to create HttpConfiguration and move these calls
                Node parent = methodCall.getParentNode().orElse(null);
                if (parent instanceof BlockStmt) {
                    BlockStmt block = (BlockStmt) parent;
                    
                    // Check if we already have HttpConfiguration in this block
                    boolean hasHttpConfig = block.getStatements().stream()
                        .anyMatch(stmt -> stmt.toString().contains("HttpConfiguration"));
                    
                    if (!hasHttpConfig) {
                        // Add HttpConfiguration creation
                        String httpConfigCode = "HttpConfiguration httpConfig = new HttpConfiguration();\n";
                        BlockStmt newBlock = StaticJavaParser.parseBlock("{" + httpConfigCode + "}");
                        block.getStatements().add(0, newBlock.getStatement(0));
                    }
                    
                    // Replace method call target
                    methodCall.setScope(StaticJavaParser.parseExpression("httpConfig"));
                    modified[0] = true;
                }
            }
        });
        
        return modified[0];
    }
    
    /**
     * Update Handler method signatures to use jakarta.servlet
     */
    private static boolean updateHandlerMethodSignatures(CompilationUnit cu) {
        boolean[] modified = {false};
        
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            String methodName = method.getNameAsString();
            if (methodName.equals("handle")) {
                // Check if this is a Handler.handle method
                if (method.getParameters().size() == 4) {
                    // Update parameter types from javax.servlet to jakarta.servlet
                    method.getParameters().forEach(param -> {
                        String paramType = param.getTypeAsString();
                        if (paramType.startsWith("javax.servlet")) {
                            param.setType(paramType.replace("javax.servlet", "jakarta.servlet"));
                            modified[0] = true;
                        }
                    });
                }
            }
        });
        
        return modified[0];
    }
}