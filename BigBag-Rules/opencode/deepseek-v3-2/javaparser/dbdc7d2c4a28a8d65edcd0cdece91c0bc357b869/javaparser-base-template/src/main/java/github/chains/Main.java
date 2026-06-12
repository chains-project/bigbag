package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        try {
            transformAllJavaFiles(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformAllJavaFiles(String sourceDir) throws IOException {
        Path start = Paths.get(sourceDir);
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                  .forEach(Main::transformFile);
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            // Create a visitor to remove enableLogging method calls
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr n, Void arg) {
                    // Check if this is a call to enableLogging method
                    if ("enableLogging".equals(n.getNameAsString())) {
                        // The enableLogging method was removed in plexus-archiver 4.4.0
                        // It was replaced with SLF4J logging internally
                        // Return null to remove this node from its parent
                        return null;
                    }
                    return super.visit(n, arg);
                }
            }, null);
            
            // Write the transformed file back
            Files.writeString(filePath, cu.toString());
            System.out.println("Transformed: " + filePath);
            
        } catch (IOException e) {
            System.err.println("Error transforming file " + filePath + ": " + e.getMessage());
        }
    }
}