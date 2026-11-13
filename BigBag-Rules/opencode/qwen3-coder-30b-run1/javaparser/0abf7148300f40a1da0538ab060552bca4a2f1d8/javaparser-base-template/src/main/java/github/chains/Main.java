package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        for (Path javaFile : javaFiles) {
            processJavaFile(javaFile);
        }
    }
    
    private static void processJavaFile(Path javaFile) throws IOException {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            
            // Find all setLineWidth calls with int arguments and replace them with Float
            // and also fix getLineWidth() used in arithmetic expressions
            new SetLineWidthVisitor().visit(cu, null);
            
            // Save the modified file
            cu.toString().getBytes("UTF-8");
            Files.write(javaFile, cu.toString().getBytes("UTF-8"));
            System.out.println("Processed: " + javaFile);
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds calls to setLineWidth(int) and replaces them with setLineWidth(Float)
     * and also fixes getLineWidth() used in arithmetic expressions
     */
    private static class SetLineWidthVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Match calls to setLineWidth with int arguments
            if (methodCall.getNameAsString().equals("setLineWidth") && 
                methodCall.getArguments().size() == 1) {
                
                Expression argExpr = methodCall.getArguments().get(0);
                
                // Handle integer literals
                if (argExpr instanceof IntegerLiteralExpr) {
                    // Replace it with a cast to Float
                    IntegerLiteralExpr intExpr = (IntegerLiteralExpr) argExpr;
                    CastExpr castExpr = new CastExpr(
                        new ClassOrInterfaceType("java.lang.Float"), 
                        intExpr
                    );
                    
                    // Replace the argument with the cast expression
                    methodCall.setArgument(0, castExpr);
                    System.out.println("Fixed setLineWidth call in: " + methodCall);
                }
                
                // Handle method calls that return int (like getLineWidth())
                else if (argExpr instanceof MethodCallExpr) {
                    MethodCallExpr methodArg = (MethodCallExpr) argExpr;
                    
                    // Check if this is a getLineWidth() call
                    if (methodArg.getNameAsString().equals("getLineWidth")) {
                        // Wrap the method call in a cast to Float
                        CastExpr castExpr = new CastExpr(
                            new ClassOrInterfaceType("java.lang.Float"), 
                            methodArg
                        );
                        
                        // Replace the argument with the cast expression
                        methodCall.setArgument(0, castExpr);
                        System.out.println("Fixed setLineWidth call with getLineWidth() in: " + methodCall);
                    }
                }
                
                // Handle NameExpr that might be int variables
                else if (argExpr instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) argExpr;
                    
                    // Wrap the name expression in a cast to Float
                    CastExpr castExpr = new CastExpr(
                        new ClassOrInterfaceType("java.lang.Float"), 
                        nameExpr
                    );
                    
                    // Replace the argument with the cast expression
                    methodCall.setArgument(0, castExpr);
                    System.out.println("Fixed setLineWidth call with variable in: " + methodCall);
                }
            }
        }
        
        @Override
        public void visit(BinaryExpr binaryExpr, Void arg) {
            super.visit(binaryExpr, arg);
            
            // Check if this is a multiplication expression involving getLineWidth()
            if (binaryExpr.getOperator().equals(BinaryExpr.Operator.MULTIPLY)) {
                // Check if left operand is getLineWidth()
                if (binaryExpr.getLeft() instanceof MethodCallExpr) {
                    MethodCallExpr methodExpr = (MethodCallExpr) binaryExpr.getLeft();
                    if (methodExpr.getNameAsString().equals("getLineWidth")) {
                        // Wrap the getLineWidth() call in a cast to Float
                        CastExpr castExpr = new CastExpr(
                            new ClassOrInterfaceType("java.lang.Float"), 
                            methodExpr
                        );
                        binaryExpr.setLeft(castExpr);
                        System.out.println("Fixed getLineWidth() in multiplication expression: " + binaryExpr);
                    }
                }
                
                // Check if right operand is getLineWidth()
                if (binaryExpr.getRight() instanceof MethodCallExpr) {
                    MethodCallExpr methodExpr = (MethodCallExpr) binaryExpr.getRight();
                    if (methodExpr.getNameAsString().equals("getLineWidth")) {
                        // Wrap the getLineWidth() call in a cast to Float
                        CastExpr castExpr = new CastExpr(
                            new ClassOrInterfaceType("java.lang.Float"), 
                            methodExpr
                        );
                        binaryExpr.setRight(castExpr);
                        System.out.println("Fixed getLineWidth() in multiplication expression: " + binaryExpr);
                    }
                }
            }
            
            // Handle addition/subtraction with Float in binary expressions
            // This is a simpler approach - we'll make sure the whole expression is handled correctly
            // by making sure the type matches what's needed
        }
    }
}
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory recursively
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        for (Path javaFile : javaFiles) {
            processJavaFile(javaFile);
        }
    }
    
    private static void processJavaFile(Path javaFile) throws IOException {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            
            // Find all setLineWidth calls with int arguments and replace them with Float
            // and also fix getLineWidth() used in arithmetic expressions
            new SetLineWidthVisitor().visit(cu, null);
            
            // Save the modified file
            cu.toString().getBytes("UTF-8");
            Files.write(javaFile, cu.toString().getBytes("UTF-8"));
            System.out.println("Processed: " + javaFile);
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds calls to setLineWidth(int) and replaces them with setLineWidth(Float)
     * and also fixes getLineWidth() used in arithmetic expressions
     */
    private static class SetLineWidthVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Match calls to setLineWidth with int arguments
            if (methodCall.getNameAsString().equals("setLineWidth") && 
                methodCall.getArguments().size() == 1) {
                
                Expression argExpr = methodCall.getArguments().get(0);
                
                // Handle integer literals
                if (argExpr instanceof IntegerLiteralExpr) {
                    // Replace it with a cast to Float
                    IntegerLiteralExpr intExpr = (IntegerLiteralExpr) argExpr;
                    CastExpr castExpr = new CastExpr(
                        new ClassOrInterfaceType("java.lang.Float"), 
                        intExpr
                    );
                    
                    // Replace the argument with the cast expression
                    methodCall.setArgument(0, castExpr);
                    System.out.println("Fixed setLineWidth call in: " + methodCall);
                }
                
                // Handle method calls that return int (like getLineWidth())
                else if (argExpr instanceof MethodCallExpr) {
                    MethodCallExpr methodArg = (MethodCallExpr) argExpr;
                    
                    // Check if this is a getLineWidth() call
                    if (methodArg.getNameAsString().equals("getLineWidth")) {
                        // Wrap the method call in a cast to Float
                        CastExpr castExpr = new CastExpr(
                            new ClassOrInterfaceType("java.lang.Float"), 
                            methodArg
                        );
                        
                        // Replace the argument with the cast expression
                        methodCall.setArgument(0, castExpr);
                        System.out.println("Fixed setLineWidth call with getLineWidth() in: " + methodCall);
                    }
                }
                
                // Handle NameExpr that might be int variables
                else if (argExpr instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) argExpr;
                    
                    // Wrap the name expression in a cast to Float
                    CastExpr castExpr = new CastExpr(
                        new ClassOrInterfaceType("java.lang.Float"), 
                        nameExpr
                    );
                    
                    // Replace the argument with the cast expression
                    methodCall.setArgument(0, castExpr);
                    System.out.println("Fixed setLineWidth call with variable in: " + methodCall);
                }
            }
        }
        
        @Override
        public void visit(BinaryExpr binaryExpr, Void arg) {
            super.visit(binaryExpr, arg);
            
            // Check if this is a multiplication expression involving getLineWidth()
            if (binaryExpr.getOperator().equals(BinaryExpr.Operator.MULTIPLY)) {
                // Check if left operand is getLineWidth()
                if (binaryExpr.getLeft() instanceof MethodCallExpr) {
                    MethodCallExpr methodExpr = (MethodCallExpr) binaryExpr.getLeft();
                    if (methodExpr.getNameAsString().equals("getLineWidth")) {
                        // Wrap the getLineWidth() call in a cast to Float
                        CastExpr castExpr = new CastExpr(
                            new ClassOrInterfaceType("java.lang.Float"), 
                            methodExpr
                        );
                        binaryExpr.setLeft(castExpr);
                        System.out.println("Fixed getLineWidth() in multiplication expression: " + binaryExpr);
                    }
                }
                
                // Check if right operand is getLineWidth()
                if (binaryExpr.getRight() instanceof MethodCallExpr) {
                    MethodCallExpr methodExpr = (MethodCallExpr) binaryExpr.getRight();
                    if (methodExpr.getNameAsString().equals("getLineWidth")) {
                        // Wrap the getLineWidth() call in a cast to Float
                        CastExpr castExpr = new CastExpr(
                            new ClassOrInterfaceType("java.lang.Float"), 
                            methodExpr
                        );
                        binaryExpr.setRight(castExpr);
                        System.out.println("Fixed getLineWidth() in multiplication expression: " + binaryExpr);
                    }
                }
            }
        }
    }
}