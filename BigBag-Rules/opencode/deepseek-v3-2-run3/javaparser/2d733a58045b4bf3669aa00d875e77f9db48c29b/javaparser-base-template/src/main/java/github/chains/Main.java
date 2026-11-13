package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Generic transformation rule for Flyway API breaking change.
 * Transforms: new Flyway() + setters → Flyway.configure().setters().load()
 * 
 * This is a generic pattern that can be adapted for similar API changes
 * where a class changes from mutable setters to fluent configuration.
 */
public class Main {
    
    // Configuration - in a real tool these would be parameters
    private static final String TARGET_CLASS = "org.flywaydb.core.Flyway";
    private static final String CONFIGURE_METHOD = "configure";
    private static final String LOAD_METHOD = "load";
    
    // Map of old setter method names to new fluent method names
    // Pattern: "setXyz" → "xyz" (lowercase first letter)
    private static final Map<String, String> METHOD_TRANSFORMS = new HashMap<>();
    static {
        METHOD_TRANSFORMS.put("setDataSource", "dataSource");
        METHOD_TRANSFORMS.put("setClassLoader", "classLoader");
        METHOD_TRANSFORMS.put("setLocations", "locations");
        METHOD_TRANSFORMS.put("setValidateOnMigrate", "validateOnMigrate");
        // Add other setter methods as needed
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        System.out.println("Target class: " + TARGET_CLASS);
        System.out.println("Method transforms: " + METHOD_TRANSFORMS);
        
        int transformedFiles = 0;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .toList();
            
            for (Path file : files) {
                if (transformFile(file)) {
                    transformedFiles++;
                }
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformedFiles + " files.");
    }
    
    private static boolean transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return false;
            }
            
            FlywayTransformer transformer = new FlywayTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Modified: " + filePath);
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
        return false;
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a new Flyway() constructor call
            if (isFlywayConstructor(n)) {
                // Get parent to understand context
                Optional<Node> parent = n.getParentNode();
                if (parent.isPresent()) {
                    // Try to find the variable declaration or expression
                    VariableFinder finder = new VariableFinder(n);
                    parent.get().accept(finder, null);
                    
                    if (finder.variableName != null && !finder.setterCalls.isEmpty()) {
                        // We found a pattern to transform!
                        modified = true;
                        return transformPattern(n, finder.variableName, finder.setterCalls);
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isFlywayConstructor(ObjectCreationExpr expr) {
            String typeName = expr.getType().toString();
            return typeName.equals(TARGET_CLASS) || 
                   typeName.equals("Flyway") ||
                   typeName.endsWith(".Flyway");
        }
        
        private Expression transformPattern(ObjectCreationExpr creationExpr, 
                                          String variableName, 
                                          List<MethodCallExpr> setterCalls) {
            // Build new expression: Flyway.configure()
            MethodCallExpr configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), CONFIGURE_METHOD);
            
            Expression currentExpr = configureCall;
            
            // Transform each setter call to fluent method call
            for (MethodCallExpr setter : setterCalls) {
                String oldMethodName = setter.getNameAsString();
                String newMethodName = METHOD_TRANSFORMS.getOrDefault(oldMethodName, 
                    Character.toLowerCase(oldMethodName.charAt(3)) + oldMethodName.substring(4));
                
                MethodCallExpr fluentCall = new MethodCallExpr(currentExpr, newMethodName);
                fluentCall.setArguments(setter.getArguments());
                currentExpr = fluentCall;
            }
            
            // Add .load() at the end
            MethodCallExpr loadCall = new MethodCallExpr(currentExpr, LOAD_METHOD);
            return loadCall;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // We'll handle method calls in the VariableFinder
            return super.visit(n, arg);
        }
    }
    
    /**
     * Helper class to find variable declarations and subsequent method calls.
     * This identifies patterns like:
     *   Flyway f = new Flyway();
     *   f.setX(...);
     *   f.setY(...);
     *   return f;
     */
    private static class VariableFinder extends ModifierVisitor<Void> {
        private final ObjectCreationExpr targetConstructor;
        private String variableName = null;
        private List<MethodCallExpr> setterCalls = new ArrayList<>();
        private boolean foundReturn = false;
        
        VariableFinder(ObjectCreationExpr targetConstructor) {
            this.targetConstructor = targetConstructor;
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Check if this variable declaration contains our target constructor
            if (n.getVariables().stream().anyMatch(v -> 
                v.getInitializer().orElse(null) == targetConstructor)) {
                // Found it! Extract variable name
                variableName = n.getVariables().get(0).getNameAsString();
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a method call on our variable
            if (variableName != null && !foundReturn) {
                Optional<Expression> scope = n.getScope();
                if (scope.isPresent() && scope.get().toString().equals(variableName)) {
                    String methodName = n.getNameAsString();
                    if (methodName.startsWith("set")) {
                        setterCalls.add(n);
                        // Remove this node from AST (will be replaced)
                        return null;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            // Check if this is returning our variable
            if (variableName != null && n.getExpression().isPresent()) {
                String expr = n.getExpression().get().toString();
                if (expr.equals(variableName)) {
                    foundReturn = true;
                }
            }
            return super.visit(n, arg);
        }
    }
}