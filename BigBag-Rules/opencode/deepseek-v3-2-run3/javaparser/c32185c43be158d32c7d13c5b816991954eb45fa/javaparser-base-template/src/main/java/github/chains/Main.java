package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway configuration in: " + sourceDir);
        
        try {
            transformDirectory(sourceDir);
            System.out.println("Transformation complete!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String sourceDir) throws IOException {
        Path startPath = Paths.get(sourceDir);
        Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    transformFile(file.toFile());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private static void transformFile(File file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
        
        cu.accept(new FlywayTransformerVisitor(), null);
        
        PrettyPrinter printer = new PrettyPrinter();
        String transformedCode = printer.print(cu);
        Files.write(file.toPath(), transformedCode.getBytes());
        System.out.println("Processed: " + file.getPath());
    }
    
    static class FlywayTransformerVisitor extends ModifierVisitor<Void> {
        private static final Map<String, String> SETTER_TO_FLUENT = createSetterMapping();
        
        private static Map<String, String> createSetterMapping() {
            Map<String, String> map = new HashMap<>();
            map.put("setDataSource", "dataSource");
            map.put("setClassLoader", "classLoader");
            map.put("setLocations", "locations");
            map.put("setValidateOnMigrate", "validateOnMigrate");
            map.put("setBaselineOnMigrate", "baselineOnMigrate");
            map.put("setBaselineVersion", "baselineVersion");
            map.put("setBaselineDescription", "baselineDescription");
            map.put("setTarget", "target");
            map.put("setTable", "table");
            map.put("setSchemas", "schemas");
            map.put("setDefaultSchema", "defaultSchema");
            map.put("setEncoding", "encoding");
            map.put("setPlaceholders", "placeholders");
            map.put("setPlaceholderPrefix", "placeholderPrefix");
            map.put("setPlaceholderSuffix", "placeholderSuffix");
            map.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
            map.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
            map.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
            map.put("setOutOfOrder", "outOfOrder");
            map.put("setCleanOnValidationError", "cleanOnValidationError");
            map.put("setCleanDisabled", "cleanDisabled");
            map.put("setMixed", "mixed");
            map.put("setGroup", "group");
            return map;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().asString().endsWith("Flyway") && n.getArguments().isEmpty()) {
                return new MethodCallExpr(
                    new NameExpr("Flyway"),
                    "configure",
                    new NodeList<>()
                );
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            if (SETTER_TO_FLUENT.containsKey(methodName)) {
                String fluentMethod = SETTER_TO_FLUENT.get(methodName);
                NodeList<Expression> args = n.getArguments();
                
                return new MethodCallExpr(
                    n.getScope().orElse(null),
                    fluentMethod,
                    args
                );
            }
            
            return super.visit(n, arg);
        }
    }
}