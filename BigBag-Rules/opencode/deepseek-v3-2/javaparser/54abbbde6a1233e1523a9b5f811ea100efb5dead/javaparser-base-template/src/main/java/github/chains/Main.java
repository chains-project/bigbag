package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <sourceDir> <removedClassFQN> [constant=value...]");
            System.err.println("Example: java -cp target/classes github.chains.Main /path/to/src com.jcabi.aspects.Tv SEVEN=7 TEN=10 MILLION=1000000");
            System.exit(1);
        }
        String sourceDir = args[0];
        String removedClassFQN = args[1];
        Map<String, String> constantMappings = new HashMap<>();
        for (int i = 2; i < args.length; i++) {
            String[] parts = args[i].split("=", 2);
            if (parts.length == 2) {
                constantMappings.put(parts[0], parts[1]);
            }
        }
        try {
            transformProject(sourceDir, removedClassFQN, constantMappings);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void transformProject(String sourceDir, String removedClassFQN, Map<String, String> constantMappings) throws Exception {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        Files.walk(sourcePath).filter(p -> p.toString().endsWith(".java")).forEach(p -> transformFile(p, removedClassFQN, constantMappings));
    }

    private static void transformFile(Path filePath, String removedClassFQN, Map<String, String> constantMappings) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(() -> new RuntimeException("Failed to parse " + filePath));
            boolean modified = false;
            modified = removeImport(cu, removedClassFQN) || modified;
            modified = replaceFieldAccesses(cu, removedClassFQN, constantMappings) || modified;
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean removeImport(CompilationUnit cu, String removedClassFQN) {
        boolean removed = false;
        List<ImportDeclaration> toRemove = new ArrayList<>();
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals(removedClassFQN)) {
                toRemove.add(importDecl);
                removed = true;
            }
        }
        for (ImportDeclaration importDecl : toRemove) {
            cu.remove(importDecl);
        }
        return removed;
    }

    private static boolean replaceFieldAccesses(CompilationUnit cu, String removedClassFQN, Map<String, String> constantMappings) {
        if (constantMappings.isEmpty()) {
            return false;
        }
        String className = removedClassFQN.substring(removedClassFQN.lastIndexOf('.') + 1);
        ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {

            @Override
            public Visitable visit(FieldAccessExpr n, Void arg) {
                if (n.getScope() instanceof NameExpr) {
                    NameExpr scope = (NameExpr) n.getScope();
                    if (scope.getNameAsString().equals(className)) {
                        String fieldName = n.getNameAsString();
                        if (constantMappings.containsKey(fieldName)) {
                            String value = constantMappings.get(fieldName);
                            try {
                                long longValue = Long.parseLong(value);
                                if (longValue <= Integer.MAX_VALUE && longValue >= Integer.MIN_VALUE) {
                                    return new IntegerLiteralExpr((int) longValue);
                                } else {
                                    return new LongLiteralExpr(longValue + "L");
                                }
                            } catch (NumberFormatException e) {
                                System.err.println("Warning: Cannot convert value '" + value + "' for field " + fieldName + " to number");
                                return n;
                            }
                        }
                    }
                }
                return super.visit(n, arg);
            }
        };
        cu.accept(visitor, null);
        return true;
    }
}
