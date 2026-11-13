package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Generic JavaParser transformation to fix Hamcrest API breaking changes.
 * Specifically addresses the issue where StringContains and StringStartsWith
 * constructors have changed and no longer accept boolean parameters.
 */
public class Main {

    public static void main(String[] args) {
        // Process all Java files recursively in the target directory
        try {
            Path sourceDir = Paths.get("/workspace/docker-adapter/src");
            processDirectory(sourceDir);
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file.toFile());
            
            // Apply transformations to fix Hamcrest constructor calls
            new HamcrestFixVisitor().visit(cu, null);
            
            // Save the modified file
            cu.save(file.toFile());
        } catch (Exception e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }

    /**
     * Visitor that fixes Hamcrest StringContains and StringStartsWith constructor calls
     */
    private static class HamcrestFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a StringContains or StringStartsWith constructor call
            if (n.getType().getNameAsString().equals("StringContains") ||
                n.getType().getNameAsString().equals("StringStartsWith")) {
                
                // Check if it has 2 arguments (boolean, string) - this is the broken pattern
                if (n.getArguments().size() == 2) {
                    Expression firstArg = n.getArguments().get(0);
                    Expression secondArg = n.getArguments().get(1);
                    
                    // If first argument is a boolean literal "false" or "true", 
                    // remove it and keep only the string argument
                    if (firstArg.isBooleanLiteralExpr() || 
                        (firstArg.isNameExpr() && 
                         (firstArg.asNameExpr().getNameAsString().equals("false") || 
                          firstArg.asNameExpr().getNameAsString().equals("true")))) {
                        
                        // Replace with just the string argument
                        n.setArguments(Collections.singletonList(secondArg));
                    }
                }
            }
        }
    }
}
