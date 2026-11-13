package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the input directory recursively
        String inputDir = args.length > 0 ? args[0] : "/workspace/nem";
        
        Files.walk(Paths.get(inputDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply the Flyway migration transformation
            FlywayTransformationVisitor visitor = new FlywayTransformationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.toString(); // This will write the modified file to disk
            cu.save(filePath.toFile());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    /**
     * Visitor to transform Flyway instantiation and configuration calls
     */
    private static class FlywayTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Flyway constructor call with no arguments
            if (n.getType().getNameAsString().equals("Flyway")) {
                // Transform: new Flyway() -> Flyway.configure().load()
                if (n.getArguments().isEmpty()) {
                    // Return the new expression: Flyway.configure().load()
                    return new MethodCallExpr(
                        new NameExpr("Flyway.configure"),
                        "load"
                    );
                }
            }
            
            return super.visit(n, arg);
        }
    }
}