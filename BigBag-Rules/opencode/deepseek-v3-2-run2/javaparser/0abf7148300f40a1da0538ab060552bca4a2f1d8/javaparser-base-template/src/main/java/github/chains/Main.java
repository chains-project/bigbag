package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            System.out.println("Processing: " + filePath);
            
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            // Apply the transformation
            cu.accept(new SetLineWidthTransformer(), null);
            
            // Write the modified file back
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(cu.toString().getBytes());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generic transformation visitor that converts int arguments to Float.valueOf() 
     * for setLineWidth method calls on JRPen instances.
     * 
     * This transformation is generic and reusable for any project affected by the
     * JasperReports 6.19.1 breaking API change where setLineWidth(int) became 
     * setLineWidth(Float).
     */
    private static class SetLineWidthTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a setLineWidth method call
            if ("setLineWidth".equals(n.getNameAsString())) {
                // Check if there's exactly one argument and it's an integer literal
                if (n.getArguments().size() == 1) {
                    Expression argument = n.getArgument(0);
                    
                    // Transform integer literals to Float.valueOf() calls
                    if (argument instanceof IntegerLiteralExpr) {
                        IntegerLiteralExpr intLiteral = (IntegerLiteralExpr) argument;
                        // Create Float.valueOf() call using static method call syntax
                        MethodCallExpr floatValueOf = new MethodCallExpr(
                            new NameExpr("Float"), "valueOf", 
                            new NodeList<>(new IntegerLiteralExpr(intLiteral.getValue()))
                        );
                        n.setArgument(0, floatValueOf);
                        System.out.println("Transformed: setLineWidth(" + intLiteral.getValue() + ") -> setLineWidth(Float.valueOf(" + intLiteral.getValue() + "))");
                    }
                    
                    // Also handle cases where the argument is an int variable/expression
                    // by wrapping it with Float.valueOf()
                    else if (argument.isIntegerLiteralExpr() || 
                             argument.isNameExpr() || 
                             argument.isFieldAccessExpr() ||
                             argument.isMethodCallExpr()) {
                        // Create Float.valueOf() wrapper
                        MethodCallExpr floatValueOf = new MethodCallExpr(
                            new NameExpr("Float"), "valueOf", 
                            new NodeList<>(argument)
                        );
                        n.setArgument(0, floatValueOf);
                        System.out.println("Transformed: setLineWidth(<int-expression>) -> setLineWidth(Float.valueOf(<int-expression>))");
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}