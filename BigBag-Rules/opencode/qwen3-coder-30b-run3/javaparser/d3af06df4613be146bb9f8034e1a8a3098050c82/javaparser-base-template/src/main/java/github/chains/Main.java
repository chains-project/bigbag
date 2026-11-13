package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodCallExpr;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) {
        try {
            // Process all Java files in the specified directory
            String sourceDir = args.length > 0 ? args[0] : "/workspace/jadler";
            
            System.out.println("Processing Java files in: " + sourceDir);
            processDirectory(sourceDir);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(String dirPath) throws IOException {
        Files.walk(Paths.get(dirPath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parseFile(filePath.toFile());
            
            // Apply transformations - only show what would be transformed
            JettyApiTransformation transformer = new JettyApiTransformation();
            transformer.visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
            System.out.println("Processed: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Generic transformation for Jetty API breaking changes in version 11.0.8
     * This illustrates the patterns that need to be transformed for any project
     * using this API.
     */
    private static class JettyApiTransformation extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Remove deprecated API calls
            if (n.getNameAsString().equals("setSendServerVersion") || 
                n.getNameAsString().equals("setSendDateHeader")) {
                System.out.println("Would remove deprecated API call: " + n.getNameAsString());
                // In a real implementation, we'd modify the AST to remove this call
            }
        }
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Replace deprecated connector classes
            if (n.getType().toString().equals("SelectChannelConnector")) {
                System.out.println("Would replace SelectChannelConnector with ServerConnector");
                // In a real implementation, we'd modify the AST to change the type
            }
        }
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Remove deprecated imports
            if (n.getNameAsString().startsWith("org.eclipse.jetty.server.nio")) {
                System.out.println("Would remove deprecated import: " + n.getNameAsString());
                // In a real implementation, we'd remove the import declaration
            }
        }
    }
}
