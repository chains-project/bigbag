package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        String sourcePath = args.length > 0 ? args[0] : "/workspace/biapi/src";
        
        System.out.println("Processing Java files in: " + sourcePath);
        
        // Find all Java files recursively
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourcePath))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        // Process each file
        for (Path file : javaFiles) {
            processFile(file);
        }
    }
    
    private static void processFile(Path filePath) throws IOException {
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Apply the transformation
            LineWidthTransformationVisitor visitor = new LineWidthTransformationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.save(filePath.toFile());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class LineWidthTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            methodCall = (MethodCallExpr) super.visit(methodCall, arg);
            
            // Look for calls to setLineWidth
            if (methodCall.getNameAsString().equals("setLineWidth")) {
                // This is a placeholder for the actual fix logic
                // The real transformation logic should go here
            }
            
            return methodCall;
        }
    }
}
    }
    
    private static void processFile(Path filePath) throws IOException {
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Apply the transformation
            LineWidthTransformationVisitor visitor = new LineWidthTransformationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.save(filePath.toFile());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class LineWidthTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            methodCall = (MethodCallExpr) super.visit(methodCall, arg);
            
            // Look for calls to setLineWidth
            if (methodCall.getNameAsString().equals("setLineWidth")) {
                // Check if we have the specific pattern: getLineBox().getPen().setLineWidth(...)
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    
                    if (scope instanceof MethodCallExpr) {
                        MethodCallExpr parentCall = (MethodCallExpr) scope;
                        if (parentCall.getNameAsString().equals("getPen") && 
                            parentCall.getScope().isPresent()) {
                            Expression grandParentScope = parentCall.getScope().get();
                            
                            if (grandParentScope instanceof MethodCallExpr) {
                                MethodCallExpr grandParentCall = (MethodCallExpr) grandParentScope;
                                if (grandParentCall.getNameAsString().equals("getLineBox")) {
                                    // Found the problematic pattern: textField.getLineBox().getPen().setLineWidth(...)
                                    NodeList<Expression> args = methodCall.getArguments();
                                    if (!args.isEmpty()) {
                                        Expression arg = args.get(0);
                                        
                                        // This is the core fix: we need to ensure that if 
                                        // the argument to setLineWidth() is an int (like from getLineWidth()),
                                        // we wrap it in Integer.valueOf() to convert it to Integer
                                        if (!(arg instanceof MethodCallExpr) || !isGetLineWidthMethod(arg)) {
                                            // If it's not already a getLineWidth() call, we might need to handle it
                                            // But the main problem is specifically with the int to Integer conversion
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return methodCall;
        }
        
        // Helper method to detect if expression is a getLineWidth() method call
        private boolean isGetLineWidthMethod(Expression expr) {
            if (expr instanceof MethodCallExpr) {
                MethodCallExpr mce = (MethodCallExpr) expr;
                return mce.getNameAsString().equals("getLineWidth");
            }
            return false;
        }
    }
}
    }
    
    private static void processFile(Path filePath) throws IOException {
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Apply the transformation
            LineWidthTransformationVisitor visitor = new LineWidthTransformationVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.save(filePath.toFile());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class LineWidthTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            methodCall = (MethodCallExpr) super.visit(methodCall, arg);
            
            // Look for calls to setLineWidth with a method call returning int/float
            if (methodCall.getNameAsString().equals("setLineWidth")) {
                // Check if the parent is getLineBox().getPen() 
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    
                    // Check if this is in the form: getLineBox().getPen()
                    if (scope instanceof MethodCallExpr) {
                        MethodCallExpr parentCall = (MethodCallExpr) scope;
                        if (parentCall.getNameAsString().equals("getPen") && 
                            parentCall.getScope().isPresent()) {
                            Expression grandParentScope = parentCall.getScope().get();
                            
                            // Check if this is in the form: textField.getLineBox()
                            if (grandParentScope instanceof MethodCallExpr) {
                                MethodCallExpr grandParentCall = (MethodCallExpr) grandParentScope;
                                if (grandParentCall.getNameAsString().equals("getLineBox")) {
                                    // This matches the pattern: textField.getLineBox().getPen().setLineWidth(...)
                                    // The original code was passing an int to setLineWidth which now requires Integer
                                    NodeList<Expression> args = methodCall.getArguments();
                                    if (!args.isEmpty()) {
                                        Expression argExpr = args.get(0);
                                        // If the argument is a method call like getLineWidth(), we need to make sure it's treated as Integer
                                        // For now, we'll add explicit conversion for int to Integer if needed
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return methodCall;
        }
    }
}
