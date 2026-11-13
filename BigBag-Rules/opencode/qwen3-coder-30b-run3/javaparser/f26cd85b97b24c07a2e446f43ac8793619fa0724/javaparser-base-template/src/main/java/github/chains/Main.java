package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.printer.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Generic JavaParser transformation to fix breaking changes in jetty-server dependency.
 * Specifically addresses the migration from deprecated connectors in Jetty 9.x to Jetty 11.x.
 * 
 * This transformation replaces:
 * - SelectChannelConnector with ServerConnector
 */
public class Main {
    
    public static void main(String[] args) {
        // Process all Java files in the specified directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jadler";
        
        try {
            processJavaFiles(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFiles(String sourceDir) throws IOException {
        Path dir = Paths.get(sourceDir);
        if (!Files.exists(dir)) {
            throw new IOException("Source directory does not exist: " + sourceDir);
        }
        
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformations
            boolean modified = transformCompilationUnit(cu);
            
            if (modified) {
                // Write back the modified content
                String newContent = cu.toString();
                Files.write(filePath, newContent.getBytes(StandardCharsets.UTF_8));
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        // Replace deprecated Connector types
        modified |= replaceDeprecatedConnectors(cu);
        
        return modified;
    }
    
    private static boolean replaceDeprecatedConnectors(CompilationUnit cu) {
        boolean modified = false;
        
        // Find object creation expressions that create deprecated connectors
        List<ObjectCreationExpr> objects = new ArrayList<>();
        cu.findAll(ObjectCreationExpr.class).forEach(objects::add);
        
        for (ObjectCreationExpr obj : objects) {
            String typeName = obj.getType().asString();
            if (typeName.contains("SelectChannelConnector")) {
                // Replace with ServerConnector
                obj.setType(new ClassOrInterfaceType("ServerConnector"));
                modified = true;
            }
        }
        
        // Remove deprecated imports
        List<ImportDeclaration> imports = new ArrayList<>();
        cu.findAll(ImportDeclaration.class).forEach(imports::add);
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.contains("SelectChannelConnector") || 
                importName.contains("org.eclipse.jetty.server.nio")) {
                importDecl.remove();
                modified = true;
            }
        }
        
        // Add ServerConnector import if needed
        boolean hasServerConnectorImport = false;
        for (ImportDeclaration importDecl : cu.findAll(ImportDeclaration.class)) {
            if (importDecl.getNameAsString().contains("ServerConnector")) {
                hasServerConnectorImport = true;
                break;
            }
        }
        
        if (!hasServerConnectorImport) {
            cu.addImport("org.eclipse.jetty.server.ServerConnector");
            modified = true;
        }
        
        return modified;
    }
}
