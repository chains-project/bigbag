package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDirectory))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))) {
            
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                try {
                    // Parse the Java file
                    CompilationUnit cu = StaticJavaParser.parse(file.toFile());
                    
                    // Apply transformation to replace getInstance() calls with INSTANCE field access
                    new TransformationVisitor().visit(cu, null);
                    
                    // Save the modified file
                    try (FileWriter writer = new FileWriter(file.toFile())) {
                        writer.write(cu.toString());
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static class TransformationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Check if this is a call to getInstance() on a strategy class
            if (methodCall.getNameAsString().equals("getInstance")) {
                // Get the expression being called on
                Expression scope = methodCall.getScope().orElse(null);
                
                if (scope instanceof NameExpr) {
                    String className = ((NameExpr) scope).getNameAsString();
                    
                    // Check if it's one of the strategy classes that had getInstance() removed
                    if (className.equals("JAXBToStringStrategy") ||
                        className.equals("JAXBEqualsStrategy") ||
                        className.equals("JAXBHashCodeStrategy") ||
                        className.equals("JAXBCopyStrategy") ||
                        className.equals("JAXBMergeStrategy")) {
                        
                        // Replace with direct field access
                        FieldAccessExpr newExpression = new FieldAccessExpr(scope, "INSTANCE");
                        
                        // Replace the method call with the field access
                        methodCall.replace(newExpression);
                    }
                }
            }
        }
    }
}