package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        
        // Create a visitor that transforms Flyway calls
        VoidVisitorAdapter<Void> visitor = new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                super.visit(n, arg);
                
                // Check if this is a new Flyway() call with no arguments
                if (n.getType().getName().asString().equals("Flyway") &&
                    n.getArguments().isEmpty() &&
                    n.getType().getScope().isPresent() &&
                    n.getType().getScope().get().asString().equals("org.flywaydb.core")) {
                    
                    // Replace new Flyway() with new Flyway(Flyway.configure())
                    n.setType(new com.github.javaparser.ast.type.ClassOrInterfaceType("Flyway"));
                    n.getArguments().add(new com.github.javaparser.ast.expr.MethodCallExpr(
                        new com.github.javaparser.ast.expr.NameExpr("Flyway"), "configure"));
                }
            }
        };
        
        // Process all Java files in the source directory
        File directory = new File(sourceDir);
        if (!directory.exists() || !directory.isDirectory()) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Recursively process all .java files
        processDirectory(directory, visitor);
    }
    
    private static void processDirectory(File directory, VoidVisitorAdapter<Void> visitor) throws IOException {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file, visitor);
                } else if (file.getName().endsWith(".java")) {
                    FileInputStream in = new FileInputStream(file);
                    CompilationUnit cu = JavaParser.parse(in);
                    cu.accept(visitor, null);
                    
                    // Write back the modified file
                    Files.write(file.toPath(), cu.toString().getBytes());
                }
            }
        }
    }
}