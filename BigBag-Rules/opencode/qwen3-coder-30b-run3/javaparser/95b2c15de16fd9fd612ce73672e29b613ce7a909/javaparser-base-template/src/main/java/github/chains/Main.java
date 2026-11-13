package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.imports.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Main {
    public static void main(String[] args) {
        // Generic transformation rule for fixing jetty dependency breaks in Java projects
        // Specifically targeting the removal of SelectChannelConnector in Jetty 11
        // Usage: java Main /path/to/source/directory
        
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/jadler";
        
        try {
            // Find all Java files in the project
            Files.walk(Paths.get(sourceDirectory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation
            TransformVisitor visitor = new TransformVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            String modifiedCode = cu.toString();
            Files.write(filePath, modifiedCode.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class TransformVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a SelectChannelConnector instantiation
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                if ("SelectChannelConnector".equals(type.getNameAsString())) {
                    // Replace with ServerConnector (Jetty 11 equivalent)
                    n.setType("ServerConnector");
                    
                    // Add server parameter as first argument for Jetty 11 constructor
                    NodeList<Expression> args = n.getArguments();
                    // We need to add 'server' as the first parameter
                    // But we need to be careful - this transformation assumes 
                    // there's a server variable available in scope
                    if (!args.isEmpty()) {
                        // Move existing arguments to preserve them
                        // and add server parameter at beginning
                        Expression serverParam = new NameExpr("server");
                        args.add(0, serverParam);
                    } else {
                        // If no arguments, add server parameter
                        args.add(new NameExpr("server"));
                    }
                }
            }
        }
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            // Remove imports of SelectChannelConnector
            if (n.getNameAsString().contains("SelectChannelConnector")) {
                n.remove();
            }
        }
    }
}