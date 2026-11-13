package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.type.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation rule to fix deprecated Jetty connector usage.
 * This rule replaces SelectChannelConnector with ServerConnector in all Java files.
 */
public class Main {
    
    public static void main(String[] args) {
        // Process all Java files in the given directory recursively
        if (args.length == 0) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<File> javaFiles = Files.walk(sourcePath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            for (File file : javaFiles) {
                processJavaFile(file);
            }
            
            System.out.println("Transformation completed successfully!");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(File file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            final boolean[] changed = {false};
            
            // Visit all object creation expressions to find deprecated connector usage
            cu.accept(new VoidVisitorAdapter<Object>() {
                @Override
                public void visit(ObjectCreationExpr expr, Object arg) {
                    super.visit(expr, arg);
                    
                    // Check if this is a SelectChannelConnector instantiation
                    if (expr.getType().isClassOrInterfaceType()) {
                        ClassOrInterfaceType type = expr.getType().asClassOrInterfaceType();
                        String typeName = type.getNameAsString();
                        
                        if ("SelectChannelConnector".equals(typeName)) {
                            // Replace with ServerConnector
                            type.setName("ServerConnector");
                            changed[0] = true;
                        }
                    }
                }
            }, null);
            
            if (changed[0]) {
                // Save the modified file
                try (PrintWriter out = new PrintWriter(file)) {
                    out.print(cu.toString());
                }
                System.out.println("Modified: " + file.getAbsolutePath());
            }
            
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error processing " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}
