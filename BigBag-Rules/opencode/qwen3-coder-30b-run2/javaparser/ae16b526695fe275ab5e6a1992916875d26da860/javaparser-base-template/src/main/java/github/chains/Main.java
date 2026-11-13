package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation tool to fix breaking dependency changes.
 * Specifically addresses Jetty API changes where SelectChannelConnector was removed.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            // Process all Java files in the directory
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix deprecated/removed Jetty connectors
            JettyConnectorTransformation transformation = new JettyConnectorTransformation();
            transformation.visit(cu, null);
            
            // In a real implementation, we would save the file here
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Transformation for fixing Jetty connector API changes.
     * Demonstrates the pattern for fixing breaking changes in dependency APIs.
     */
    private static class JettyConnectorTransformation extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ConstructorDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Look for constructors that create deprecated connectors
            n.findAll(ObjectCreationExpr.class).forEach(this::transformDeprecatedConnectorCreation);
        }
        
        @Override
        public void visit(FieldDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Look for field initializations that create deprecated connectors
            n.findAll(ObjectCreationExpr.class).forEach(this::transformDeprecatedConnectorCreation);
        }
        
        private void transformDeprecatedConnectorCreation(ObjectCreationExpr creationExpr) {
            if (creationExpr.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) creationExpr.getType();
                if ("SelectChannelConnector".equals(type.getNameAsString())) {
                    System.out.println("Found deprecated SelectChannelConnector");
                    
                    // This is where a real implementation would:
                    // 1. Replace the deprecated type with a new one (e.g., ServerConnector)
                    // 2. Update imports accordingly
                    // 3. Modify method calls if signatures changed
                }
            }
        }
    }
}