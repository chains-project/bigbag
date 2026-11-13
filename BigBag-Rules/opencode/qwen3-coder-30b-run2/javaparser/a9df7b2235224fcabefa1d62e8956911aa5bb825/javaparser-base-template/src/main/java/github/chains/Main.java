package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation rule for fixing SnakeYAML breaking changes
 * Specifically addresses the IntrospectionException compatibility issue in getProperties method
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dirPath = Paths.get(sourceDir);
        
        // Walk through all Java files in the directory
        Files.walk(dirPath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply the transformation
            new SnakeYAMLFixVisitor().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that finds and fixes getProperties method declarations with wrong exception throws clauses
     */
    private static class SnakeYAMLFixVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            
            // Look for getProperties method declarations
            if (md.getNameAsString().equals("getProperties") && 
                md.getParameters().size() == 1 &&
                md.getParameters().get(0).getType().asString().equals("Class<? extends Object>")) {
                
                // Check if it declares IntrospectionException in throws clause
                boolean hasIntrospectionException = md.getThrownExceptions().stream()
                    .anyMatch(type -> type.asString().contains("IntrospectionException"));
                
                if (hasIntrospectionException) {
                    // Remove the IntrospectionException from throws clause
                    md.getThrownExceptions().removeIf(type -> type.asString().contains("IntrospectionException"));
                    
                    System.out.println("Fixed getProperties method");
                }
            }
        }
    }
}
