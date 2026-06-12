package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: Main <source_directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        
        try {
            // Process all Java files in the source directory
            Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(java.nio.file.Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations for commons-io breaking changes
            transformCommonsIOImports(cu);
            transformBoundedInputStream(cu);
            transformClosedInputStream(cu);
            transformThresholdingOutputStream(cu);
            transformNullPrintStream(cu);
            transformGetByteCount(cu);
            
            // Write back the modified content
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static void transformCommonsIOImports(CompilationUnit cu) {
        // Remove problematic imports
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ImportDeclaration n, Void arg) {
                String importName = n.getNameAsString();
                if (importName.equals("org.apache.commons.io.input.BoundedInputStream") ||
                    importName.equals("org.apache.commons.io.input.ClosedInputStream") ||
                    importName.equals("org.apache.commons.io.output.ThresholdingOutputStream") ||
                    importName.equals("org.apache.commons.io.output.NullPrintStream")) {
                    n.remove();
                }
                super.visit(n, arg);
            }
        }, null);
    }
    
    private static void transformBoundedInputStream(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                if (n.getType().getNameAsString().equals("BoundedInputStream")) {
                    // Replace with CountingInputStream but only use first parameter
                    NodeList<Expression> args = n.getArguments();
                    if (args.size() >= 1) {
                        Expression inputStream = args.get(0);
                        // Create a new CountingInputStream with just the input stream
                        ObjectCreationExpr newCreation = new ObjectCreationExpr(null, 
                            new Name("CountingInputStream"), new NodeList<>(inputStream));
                        n.replace(newCreation);
                    }
                }
                super.visit(n, arg);
            }
        }, null);
    }
    
    private static void transformClosedInputStream(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                if (n.getType().getNameAsString().equals("ClosedInputStream")) {
                    // Replace with CountingInputStream (no parameters)
                    ObjectCreationExpr newCreation = new ObjectCreationExpr(null, 
                        new Name("CountingInputStream"), new NodeList<>());
                    n.replace(newCreation);
                }
                super.visit(n, arg);
            }
        }, null);
    }
    
    private static void transformThresholdingOutputStream(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                if (n.getType().getNameAsString().equals("ThresholdingOutputStream")) {
                    // Replace with CountingOutputStream
                    ObjectCreationExpr newCreation = new ObjectCreationExpr(null, 
                        new Name("CountingOutputStream"), n.getArguments());
                    n.replace(newCreation);
                }
                super.visit(n, arg);
            }
        }, null);
    }
    
    private static void transformNullPrintStream(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr n, Void arg) {
                if (n.getType().getNameAsString().equals("NullPrintStream")) {
                    // Replace with PrintStream.nullOutputStream()
                    MethodCallExpr nullOutputStream = new MethodCallExpr(
                        new NameExpr("java.io.PrintStream"), "nullOutputStream");
                    n.replace(nullOutputStream);
                }
                super.visit(n, arg);
            }
        }, null);
    }
    
    private static void transformGetByteCount(CompilationUnit cu) {
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals("getByteCount")) {
                    if (n.getScope().isPresent() && 
                        n.getScope().get() instanceof NameExpr) {
                        NameExpr scope = (NameExpr) n.getScope().get();
                        if (scope.getNameAsString().contains("CountingInputStream")) {
                            n.setName("getCount");
                        }
                    }
                }
                super.visit(n, arg);
            }
        }, null);
    }
}