package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A generic JavaParser transformation rule for API migration patterns.
 * 
 * This transformation handles the common pattern where:
 * 1. A class changes from using a no-arg constructor + setters
 * 2. To using a static factory method + fluent builder + terminal method
 * 
 * Example: Flyway 9.19.4 migration
 *   Old: Flyway flyway = new Flyway(); flyway.setX(...); return flyway;
 *   New: return Flyway.configure().x(...).load();
 * 
 * The transformation is parameterized by:
 * - targetClass: The fully-qualified class name (e.g., "org.flywaydb.core.Flyway")
 * - factoryMethod: The static factory method (e.g., "configure")
 * - terminalMethod: The method that creates the instance (e.g., "load")
 * - methodMappings: Map from setter names to builder method names
 */
public class Main {
    // Configuration for Flyway 9.19.4 API migration
    private static final String TARGET_CLASS = "org.flywaydb.core.Flyway";
    private static final String FACTORY_METHOD = "configure";
    private static final String TERMINAL_METHOD = "load";
    private static final Map<String, String> METHOD_MAPPINGS = new HashMap<>();
    
    static {
        METHOD_MAPPINGS.put("setDataSource", "dataSource");
        METHOD_MAPPINGS.put("setLocations", "locations");
        METHOD_MAPPINGS.put("setValidateOnMigrate", "validateOnMigrate");
        METHOD_MAPPINGS.put("setBaselineOnMigrate", "baselineOnMigrate");
        METHOD_MAPPINGS.put("setBaselineVersion", "baselineVersion");
        METHOD_MAPPINGS.put("setTable", "table");
        METHOD_MAPPINGS.put("setEncoding", "encoding");
        // Note: setClassLoader needs special handling - can be passed to configure()
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms API usage patterns for breaking changes.");
            System.err.println("Current configuration targets: " + TARGET_CLASS);
            System.err.println("Transformation: new X() -> X." + FACTORY_METHOD + "()");
            System.err.println("               x.setY(z) -> .y(z)");
            System.err.println("               return x -> return x." + TERMINAL_METHOD + "()");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        System.out.println("Target class: " + TARGET_CLASS);
        System.out.println("Method mappings: " + METHOD_MAPPINGS);
        
        int modifiedCount = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                modifiedCount++;
                System.out.println("Modified: " + sourceDir.relativize(javaFile));
            }
        }
        
        System.out.println("\nProcessing complete. Modified " + modifiedCount + " files.");
        System.out.println("The transformation rule is generic and can be applied to");
        System.out.println("any project by changing the configuration parameters.");
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + filePath)
        );
        
        ApiMigrationVisitor visitor = new ApiMigrationVisitor();
        cu.accept(visitor, null);
        
        if (visitor.wasModified()) {
            Files.write(filePath, cu.toString().getBytes());
            return true;
        }
        return false;
    }
    
    static class ApiMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private Map<String, List<MethodCallExpr>> pendingSetters = new HashMap<>();
        private Map<String, String> variableTypes = new HashMap<>();
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            String typeName = n.getVariable(0).getType().asString();
            String varName = n.getVariable(0).getNameAsString();
            
            // Check if this is our target type (simple or fully-qualified)
            boolean isTargetType = typeName.equals(getSimpleName(TARGET_CLASS)) || 
                                   typeName.equals(TARGET_CLASS);
            
            if (isTargetType) {
                Optional<Expression> initializer = n.getVariable(0).getInitializer();
                if (initializer.isPresent() && initializer.get() instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) initializer.get();
                    String creationType = creation.getType().asString();
                    
                    if ((creationType.equals(getSimpleName(TARGET_CLASS)) || 
                         creationType.equals(TARGET_CLASS)) && 
                        creation.getArguments().isEmpty()) {
                        
                        modified = true;
                        variableTypes.put(varName, typeName);
                        pendingSetters.put(varName, new ArrayList<>());
                        
                        // Replace new X() with X.factoryMethod()
                        String factoryCall = typeName.equals(getSimpleName(TARGET_CLASS)) ? 
                            getSimpleName(TARGET_CLASS) + "." + FACTORY_METHOD + "()" :
                            TARGET_CLASS + "." + FACTORY_METHOD + "()";
                        
                        JavaParser parser = new JavaParser();
                        Expression factoryExpr = parser.parseExpression(factoryCall).getResult().orElseThrow();
                        n.getVariable(0).setInitializer(factoryExpr);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            if (METHOD_MAPPINGS.containsKey(methodName)) {
                Optional<Expression> scope = n.getScope();
                if (scope.isPresent() && scope.get() instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope.get();
                    String varName = nameExpr.getNameAsString();
                    
                    if (pendingSetters.containsKey(varName)) {
                        modified = true;
                        pendingSetters.get(varName).add(n);
                        return null; // Remove this setter call
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            Visitable result = super.visit(n, arg);
            
            if (result == null) {
                return null;
            }
            
            // Check if this is a variable declaration with pending setters
            if (n.getExpression() instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) n.getExpression();
                String varName = varDecl.getVariable(0).getNameAsString();
                
                if (pendingSetters.containsKey(varName) && !pendingSetters.get(varName).isEmpty()) {
                    rebuildWithBuilderPattern(varDecl, varName);
                    pendingSetters.remove(varName);
                }
            }
            
            return result;
        }
        
        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            Visitable result = super.visit(n, arg);
            
            // Check if returning a transformed variable
            Optional<Expression> expr = n.getExpression();
            if (expr.isPresent() && expr.get() instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) expr.get();
                String varName = nameExpr.getNameAsString();
                
                if (variableTypes.containsKey(varName)) {
                    // This variable was transformed, add terminal method
                    modified = true;
                    MethodCallExpr terminalCall = new MethodCallExpr(nameExpr, TERMINAL_METHOD);
                    n.setExpression(terminalCall);
                    variableTypes.remove(varName);
                }
            }
            
            return result;
        }
        
        private void rebuildWithBuilderPattern(VariableDeclarationExpr varDecl, String varName) {
            Optional<Expression> initializerOpt = varDecl.getVariable(0).getInitializer();
            if (!initializerOpt.isPresent() || !(initializerOpt.get() instanceof MethodCallExpr)) {
                return;
            }
            
            MethodCallExpr builderChain = (MethodCallExpr) initializerOpt.get();
            List<MethodCallExpr> setters = pendingSetters.get(varName);
            
            for (MethodCallExpr setter : setters) {
                String oldMethodName = setter.getNameAsString();
                String newMethodName = METHOD_MAPPINGS.get(oldMethodName);
                
                if (newMethodName != null) {
                    NodeList<Expression> args = setter.getArguments();
                    builderChain = new MethodCallExpr(builderChain, newMethodName, args);
                }
            }
            
            varDecl.getVariable(0).setInitializer(builderChain);
        }
        
        private String getSimpleName(String fqName) {
            return fqName.substring(fqName.lastIndexOf('.') + 1);
        }
    }
}