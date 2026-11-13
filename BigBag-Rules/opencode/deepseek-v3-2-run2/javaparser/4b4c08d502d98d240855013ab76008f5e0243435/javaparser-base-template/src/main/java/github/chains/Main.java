package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(javaFiles::add);

        System.out.println("Found " + javaFiles.size() + " Java files");

        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            processFile(javaFile);
        }

        System.out.println("Transformation complete!");
    }

    private static void processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = StaticJavaParser.parse(content);

        FlywayMigrationVisitor visitor = new FlywayMigrationVisitor();
        cu.accept(visitor, null);

        if (visitor.wasModified()) {
            Files.writeString(filePath, cu.toString());
            System.out.println("  -> Modified: " + filePath);
        }
    }

    private static class FlywayMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;

        public boolean wasModified() {
            return modified;
        }

        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Look for methods that contain Flyway configuration
            BlockStmt body = n.getBody().orElse(null);
            if (body != null) {
                // Check if this method contains Flyway configuration
                boolean hasFlywayConfig = body.findAll(ObjectCreationExpr.class).stream()
                        .anyMatch(expr -> expr.getType().asClassOrInterfaceType().getNameAsString().equals("Flyway"));
                
                if (hasFlywayConfig) {
                    // Process the method body to transform Flyway configuration
                    return super.visit(n, arg);
                }
            }
            return n;
        }

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Match: new Flyway()
            if (n.getType().asClassOrInterfaceType().getNameAsString().equals("Flyway") &&
                n.getArguments().isEmpty()) {
                
                // Replace with: Flyway.configure()
                MethodCallExpr configureCall = new MethodCallExpr(
                    new NameExpr("Flyway"), "configure");
                
                modified = true;
                return configureCall;
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Match: flyway.setDataSource(dataSource)
            if (n.getNameAsString().equals("setDataSource") && 
                n.getArguments().size() == 1) {
                
                // Replace with: .dataSource(dataSource)
                return transformToFluentMethod(n, "dataSource");
            }
            
            // Match: flyway.setClassLoader(classLoader)
            if (n.getNameAsString().equals("setClassLoader") && 
                n.getArguments().size() == 1) {
                
                // Note: classLoader is passed to configure() constructor, not as fluent method
                // This requires more complex transformation
                return n;
            }
            
            // Match: flyway.setLocations(locations)
            if (n.getNameAsString().equals("setLocations") && 
                n.getArguments().size() == 1) {
                
                // Replace with: .locations(locations)
                return transformToFluentMethod(n, "locations");
            }
            
            // Match: flyway.setValidateOnMigrate(validate)
            if (n.getNameAsString().equals("setValidateOnMigrate") && 
                n.getArguments().size() == 1) {
                
                // Replace with: .validateOnMigrate(validate)
                return transformToFluentMethod(n, "validateOnMigrate");
            }
            
            return super.visit(n, arg);
        }

        private MethodCallExpr transformToFluentMethod(MethodCallExpr oldCall, String newMethodName) {
            // Extract the scope (flyway variable)
            Expression scope = oldCall.getScope().orElse(null);
            if (scope == null) {
                return oldCall;
            }

            // Create new method call: scope.newMethodName(args...)
            MethodCallExpr newCall = new MethodCallExpr(scope, newMethodName);
            newCall.setArguments(oldCall.getArguments());
            
            modified = true;
            return newCall;
        }
    }
}