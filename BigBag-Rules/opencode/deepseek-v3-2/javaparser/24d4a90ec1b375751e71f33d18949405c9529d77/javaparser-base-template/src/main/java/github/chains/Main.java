package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-directory> <fully-qualified-class-name>");
            System.err.println("Example: java github.chains.Main /path/to/src com.jcabi.aspects.Tv");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String className = args[1];
        
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        Map<String, Integer> fieldMap = createFieldMap();
        JavaParser parser = new JavaParser();
        
        Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> processFile(p, parser, className, fieldMap));
        
        System.out.println("Transformation complete.");
    }
    
    private static Map<String, Integer> createFieldMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("FIVE", 5);
        map.put("HUNDRED", 100);
        map.put("THOUSAND", 1000);
        map.put("MILLION", 1000000);
        map.put("BILLION", 1000000000);
        return map;
    }
    
    private static void processFile(Path filePath, JavaParser parser, String className, Map<String, Integer> fieldMap) {
        try (FileInputStream in = new FileInputStream(filePath.toFile())) {
            CompilationUnit cu = parser.parse(in).getResult().orElseThrow();
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            final boolean[] foundUsage = {false};
            
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(FieldAccessExpr n, Void arg) {
                    if (n.getScope().isNameExpr()) {
                        NameExpr scope = n.getScope().asNameExpr();
                        String fieldName = n.getNameAsString();
                        
                        if (fieldMap.containsKey(fieldName)) {
                            if (scope.getNameAsString().equals(simpleClassName)) {
                                foundUsage[0] = true;
                                System.out.println("Replacing " + className + "." + fieldName + 
                                                 " with " + fieldMap.get(fieldName) + 
                                                 " in " + filePath);
                                
                                return new IntegerLiteralExpr(fieldMap.get(fieldName).toString());
                            }
                        }
                    }
                    return super.visit(n, arg);
                }
            }, null);
            
            if (!foundUsage[0]) {
                Optional<ImportDeclaration> importToRemove = cu.getImports().stream()
                    .filter(imp -> imp.getNameAsString().equals(className))
                    .findFirst();
                
                if (importToRemove.isPresent()) {
                    System.out.println("Removing unused import: " + className + " from " + filePath);
                    cu.getImports().remove(importToRemove.get());
                }
            }
            
            Files.write(filePath, cu.toString().getBytes());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}