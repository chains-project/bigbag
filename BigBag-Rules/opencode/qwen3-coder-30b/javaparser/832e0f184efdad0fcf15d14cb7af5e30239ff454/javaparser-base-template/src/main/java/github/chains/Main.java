package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        try (Stream<Path> paths = Files.walk(sourcePath).filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java"))) {
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                processFile(file);
            }
        }
    }
    
    private static void processFile(Path file) throws IOException {
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            // Create a new visitor instance to modify the AST
            ParseEnchantmentVisitor visitor = new ParseEnchantmentVisitor();
            cu.accept(visitor, null);
            
            // Write the modified content back to the file
            try (FileWriter writer = new FileWriter(file.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    private static class ParseEnchantmentVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            super.visit(methodCall, arg);
            
            // Look for the pattern: XEnchantment.matchXEnchantment(...).get().parseEnchantment(...)
            if (methodCall.getNameAsString().equals("parseEnchantment")) {
                // Check if this is a chained call of the form: XEnchantment.matchXEnchantment(...).get().parseEnchantment(...)
                Expression scope = methodCall.getScope().orElse(null);
                if (scope instanceof MethodCallExpr) {
                    MethodCallExpr scopeMethodCall = (MethodCallExpr) scope;
                    if (scopeMethodCall.getNameAsString().equals("get")) {
                        Expression scopeOfScope = scopeMethodCall.getScope().orElse(null);
                        if (scopeOfScope instanceof MethodCallExpr) {
                            MethodCallExpr scopeOfScopeMethodCall = (MethodCallExpr) scopeOfScope;
                            if (scopeOfScopeMethodCall.getNameAsString().equals("matchXEnchantment")) {
                                // Found the problematic pattern - this is the chain we need to fix
                                Expression parentScope = scopeOfScopeMethodCall.getScope().orElse(null);
                                if (parentScope instanceof NameExpr) {
                                    NameExpr nameExpr = (NameExpr) parentScope;
                                    if (nameExpr.getNameAsString().equals("XEnchantment")) {
                                        System.out.println("Found parseEnchantment call in file: " + methodCall.getRange().orElseThrow().begin.toString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}