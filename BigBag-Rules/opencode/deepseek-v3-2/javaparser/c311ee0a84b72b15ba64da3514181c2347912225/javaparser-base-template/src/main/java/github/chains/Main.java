package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Invalid source directory: " + sourceDir);
            System.exit(1);
        }
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + file)
            );
            
            HamcrestConstructorVisitor visitor = new HamcrestConstructorVisitor();
            cu.accept(visitor, null);
            
            // Always write back since we modify in place
            Files.write(file, cu.toString().getBytes());
            System.out.println("Processed: " + file);
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    private static class HamcrestConstructorVisitor extends ModifierVisitor<Void> {
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            // Check if this is a StringContains or StringStartsWith constructor call
            if (expr.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = expr.getType().asClassOrInterfaceType();
                String typeName = type.getNameAsString();
                
                if ("StringContains".equals(typeName)) {
                    // Handle StringContains constructor calls
                    if (expr.getArguments().size() == 2) {
                        // If it has 2 arguments (boolean, String), remove the boolean
                        // and use Matchers.containsString instead
                        expr.getArguments().remove(0);
                        // Change to static method call: Matchers.containsString(...)
                        com.github.javaparser.ast.expr.MethodCallExpr methodCall = 
                            new com.github.javaparser.ast.expr.MethodCallExpr(
                                new com.github.javaparser.ast.expr.NameExpr("Matchers"),
                                "containsString",
                                expr.getArguments()
                            );
                        expr.replace(methodCall);
                    } else if (expr.getArguments().size() == 1) {
                        // If it has 1 argument (String), also use Matchers.containsString
                        com.github.javaparser.ast.expr.MethodCallExpr methodCall = 
                            new com.github.javaparser.ast.expr.MethodCallExpr(
                                new com.github.javaparser.ast.expr.NameExpr("Matchers"),
                                "containsString",
                                expr.getArguments()
                            );
                        expr.replace(methodCall);
                    }
                } else if ("StringStartsWith".equals(typeName)) {
                    // Handle StringStartsWith constructor calls
                    if (expr.getArguments().size() == 2) {
                        // If it has 2 arguments (boolean, String), remove the boolean
                        // and use Matchers.startsWith instead
                        expr.getArguments().remove(0);
                        com.github.javaparser.ast.expr.MethodCallExpr methodCall = 
                            new com.github.javaparser.ast.expr.MethodCallExpr(
                                new com.github.javaparser.ast.expr.NameExpr("Matchers"),
                                "startsWith",
                                expr.getArguments()
                            );
                        expr.replace(methodCall);
                    } else if (expr.getArguments().size() == 1) {
                        // If it has 1 argument (String), also use Matchers.startsWith
                        com.github.javaparser.ast.expr.MethodCallExpr methodCall = 
                            new com.github.javaparser.ast.expr.MethodCallExpr(
                                new com.github.javaparser.ast.expr.NameExpr("Matchers"),
                                "startsWith",
                                expr.getArguments()
                            );
                        expr.replace(methodCall);
                    }
                }
            }
            return expr;
        }
    }
}