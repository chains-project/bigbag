package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Generic transformation rule for Flyway API migration from pre-9.x to 9.18.0+
 * 
 * Transforms:
 *   Old: new Flyway() + setters (setDataSource, setClassLoader, setLocations, setValidateOnMigrate)
 *   New: Flyway.configure() + fluent configuration + load()
 * 
 * This rule is generic and can be applied to any Java project affected by
 * the Flyway 9.x breaking API change.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Flyway API usage from pre-9.x to 9.18.0+");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> transformFile(p));
        
        System.out.println("Transformation complete.");
    }
    
    private static void transformFile(Path filePath) {
        try {
            String originalContent = Files.readString(filePath);
            
            // Skip files without Flyway usage to improve performance
            if (!originalContent.contains("new Flyway") && 
                !originalContent.contains("new org.flywaydb.core.Flyway")) {
                return;
            }
            
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Warning: Could not parse " + filePath);
                return;
            }
            
            FlywayTransformer transformer = new FlywayTransformer();
            cu.accept(transformer, null);
            
            String transformedContent = cu.toString();
            if (!transformedContent.equals(originalContent)) {
                Files.write(filePath, transformedContent.getBytes());
                System.out.println("Transformed: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        // Track Flyway variable declarations and their setters
        private final Map<String, ObjectCreationExpr> flywayCreations = new HashMap<>();
        private final Map<String, List<MethodCallExpr>> variableSetters = new HashMap<>();
        private final Set<MethodCallExpr> settersToRemove = new HashSet<>();
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            String typeName = n.getType().asString();
            
            // Match both simple and fully qualified Flyway class names
            if (typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway")) {
                if (n.getArguments().isEmpty()) { // new Flyway() constructor
                    // Find the variable name if this is part of a declaration
                    n.getParentNode().ifPresent(parent -> {
                        if (parent instanceof VariableDeclarationExpr) {
                            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) parent;
                            if (varDecl.getVariables().size() == 1) {
                                String varName = varDecl.getVariables().get(0).getNameAsString();
                                flywayCreations.put(varName, n);
                            }
                        }
                    });
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String varName = ((NameExpr) scope).getNameAsString();
                    String methodName = n.getNameAsString();
                    
                    // Track Flyway setter methods
                    if (methodName.equals("setDataSource") || 
                        methodName.equals("setClassLoader") || 
                        methodName.equals("setLocations") || 
                        methodName.equals("setValidateOnMigrate")) {
                        
                        variableSetters.computeIfAbsent(varName, k -> new ArrayList<>()).add(n);
                        settersToRemove.add(n);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Remove setter statements during traversal
            if (n.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr call = (MethodCallExpr) n.getExpression();
                if (settersToRemove.contains(call)) {
                    n.remove();
                    return null;
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // After visiting children, transform Flyway creation if needed
            Visitable result = super.visit(n, arg);
            
            if (n.getVariables().size() == 1) {
                String varName = n.getVariables().get(0).getNameAsString();
                
                if (flywayCreations.containsKey(varName) && variableSetters.containsKey(varName)) {
                    // Transform this Flyway creation
                    ObjectCreationExpr creation = flywayCreations.get(varName);
                    List<MethodCallExpr> setters = variableSetters.get(varName);
                    
                    Expression newExpression = buildNewFlywayExpression(setters);
                    creation.replace(newExpression);
                }
            }
            
            return result;
        }
        
        private Expression buildNewFlywayExpression(List<MethodCallExpr> setters) {
            // Start with Flyway.configure() or Flyway.configure(classLoader)
            MethodCallExpr configureCall;
            
            Optional<MethodCallExpr> classLoaderSetter = setters.stream()
                .filter(s -> s.getNameAsString().equals("setClassLoader"))
                .findFirst();
            
            if (classLoaderSetter.isPresent()) {
                Expression classLoaderArg = classLoaderSetter.get().getArgument(0);
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure", 
                    new NodeList<>(classLoaderArg));
            } else {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            }
            
            // Build fluent configuration chain
            MethodCallExpr chain = configureCall;
            
            for (MethodCallExpr setter : setters) {
                String methodName = setter.getNameAsString();
                
                if (methodName.equals("setClassLoader")) {
                    continue; // Already handled in configure()
                }
                
                String fluentMethod = mapSetterToFluentMethod(methodName);
                if (fluentMethod != null) {
                    chain = new MethodCallExpr(chain, fluentMethod, setter.getArguments());
                }
            }
            
            // End with .load()
            return new MethodCallExpr(chain, "load");
        }
        
        private String mapSetterToFluentMethod(String setterName) {
            switch (setterName) {
                case "setDataSource": return "dataSource";
                case "setLocations": return "locations";
                case "setValidateOnMigrate": return "validateOnMigrate";
                default: return null;
            }
        }
    }
}