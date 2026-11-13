package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> transformFile(p));
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            
            FlywayTransformer transformer = new FlywayTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private Map<String, List<MethodCallExpr>> flywaySetters = new HashMap<>();
        private Map<String, Node> variableReplacements = new HashMap<>();
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Reset state for each method
            flywaySetters.clear();
            variableReplacements.clear();
            
            // First pass: collect all Flyway variable declarations and their setters
            super.visit(n, arg);
            
            // Second pass: apply transformations
            if (!flywaySetters.isEmpty()) {
                applyTransformations(n);
                modified = true;
            }
            
            return n;
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Check for Flyway variable declarations
            for (VariableDeclarator vd : n.getVariables()) {
                String typeName = vd.getType().asString();
                if ("Flyway".equals(typeName) || "org.flywaydb.core.Flyway".equals(typeName)) {
                    // Check if initialized with new Flyway()
                    if (vd.getInitializer().isPresent() && 
                        vd.getInitializer().get() instanceof ObjectCreationExpr) {
                        
                        ObjectCreationExpr oce = (ObjectCreationExpr) vd.getInitializer().get();
                        String constructorType = oce.getType().asString();
                        if ("Flyway".equals(constructorType) || "org.flywaydb.core.Flyway".equals(constructorType)) {
                            String varName = vd.getNameAsString();
                            flywaySetters.put(varName, new ArrayList<>());
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for Flyway setter methods
            if (n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String varName = ((NameExpr) scope).getNameAsString();
                    if (flywaySetters.containsKey(varName)) {
                        String methodName = n.getNameAsString();
                        // Map old setter names to new fluent API names
                        Map<String, String> methodMap = new HashMap<>();
                        methodMap.put("setDataSource", "dataSource");
                        methodMap.put("setClassLoader", "classLoader");
                        methodMap.put("setLocations", "locations");
                        methodMap.put("setValidateOnMigrate", "validateOnMigrate");
                        
                        if (methodMap.containsKey(methodName)) {
                            // Store the method call for later transformation
                            flywaySetters.get(varName).add(n);
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            // Check for return statements returning a Flyway variable
            if (n.getExpression().isPresent()) {
                Expression expr = n.getExpression().get();
                if (expr instanceof NameExpr) {
                    String varName = ((NameExpr) expr).getNameAsString();
                    if (flywaySetters.containsKey(varName) && !variableReplacements.containsKey(varName)) {
                        // Build the fluent API chain
                        MethodCallExpr chain = buildFluentChain(varName);
                        if (chain != null) {
                            n.setExpression(chain);
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private MethodCallExpr buildFluentChain(String varName) {
            List<MethodCallExpr> setters = flywaySetters.get(varName);
            if (setters == null || setters.isEmpty()) {
                return null;
            }
            
            // Check if there's a setClassLoader call
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                String oldMethodName = setter.getNameAsString();
                if ("setClassLoader".equals(oldMethodName)) {
                    // Store the class loader argument to pass to configure()
                    classLoaderArg = setter.getArgument(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            // Start with Flyway.configure() or Flyway.configure(classLoader)
            MethodCallExpr configureCall;
            if (classLoaderArg != null) {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure", new NodeList<>(classLoaderArg));
            } else {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            }
            
            // Add all other setters as fluent method calls
            MethodCallExpr currentChain = configureCall;
            for (MethodCallExpr setter : otherSetters) {
                String oldMethodName = setter.getNameAsString();
                String newMethodName = mapMethodName(oldMethodName);
                if (newMethodName != null) {
                    currentChain = new MethodCallExpr(currentChain, newMethodName, setter.getArguments());
                }
            }
            
            // Add .load() at the end
            return new MethodCallExpr(currentChain, "load");
        }
        
        private String mapMethodName(String oldMethodName) {
            switch (oldMethodName) {
                case "setDataSource": return "dataSource";
                case "setClassLoader": return "classLoader";
                case "setLocations": return "locations";
                case "setValidateOnMigrate": return "validateOnMigrate";
                default: return null;
            }
        }
        
        private void applyTransformations(MethodDeclaration method) {
            // Remove the original variable declaration and setter statements
            // while building the fluent chain in the return statement
            BlockStmt body = method.getBody().orElse(null);
            if (body != null) {
                List<Statement> newStatements = new ArrayList<>();
                Set<String> flywayVars = flywaySetters.keySet();
                Set<MethodCallExpr> setterCalls = new HashSet<>();
                
                // Collect all setter calls
                for (List<MethodCallExpr> setters : flywaySetters.values()) {
                    setterCalls.addAll(setters);
                }
                
                // Keep all statements except Flyway variable declarations and their setters
                for (Statement stmt : body.getStatements()) {
                    boolean keep = true;
                    
                    // Check if this is a Flyway variable declaration
                    if (stmt instanceof ExpressionStmt) {
                        Expression expr = ((ExpressionStmt) stmt).getExpression();
                        if (expr instanceof VariableDeclarationExpr) {
                            VariableDeclarationExpr vde = (VariableDeclarationExpr) expr;
                            for (VariableDeclarator vd : vde.getVariables()) {
                                if (flywayVars.contains(vd.getNameAsString())) {
                                    keep = false;
                                    break;
                                }
                            }
                        }
                    }
                    
                    // Check if this is a Flyway setter call
                    if (stmt instanceof ExpressionStmt) {
                        Expression expr = ((ExpressionStmt) stmt).getExpression();
                        if (expr instanceof MethodCallExpr && setterCalls.contains(expr)) {
                            keep = false;
                        }
                    }
                    
                    if (keep) {
                        newStatements.add(stmt);
                    }
                }
                
                body.setStatements(new NodeList<>(newStatements));
            }
        }
    }
}