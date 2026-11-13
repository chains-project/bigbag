package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.body.VariableDeclarator;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    // Configuration: These could be parameters for different dependency upgrades
    private static final String TARGET_CLASS = "org.flywaydb.core.Flyway";
    private static final String CONFIGURE_METHOD = "configure";
    private static final String LOAD_METHOD = "load";
    
    // Special handling for setClassLoader -> configure(classLoader)
    private static final String SPECIAL_SETTER = "setClassLoader";
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        transformFiles(sourceDir);
    }
    
    private static void transformFiles(Path sourceDir) throws IOException {
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        int transformed = 0;
        for (Path file : javaFiles) {
            if (transformFile(file)) {
                transformed++;
            }
        }
        
        System.out.println("Transformed " + transformed + " files");
    }
    
    private static boolean transformFile(Path file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElse(null);
        if (cu == null) {
            return false;
        }
        
        BuilderPatternTransformer transformer = new BuilderPatternTransformer();
        cu.accept(transformer, null);
        
        if (transformer.wasTransformed()) {
            Files.write(file, cu.toString().getBytes());
            return true;
        }
        return false;
    }
    
    private static class BuilderPatternTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        private Map<String, List<MethodCallExpr>> targetVariables = new HashMap<>();
        private Map<String, ReturnStmt> variableReturns = new HashMap<>();
        private Map<String, Expression> specialArguments = new HashMap<>();
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Look for: TargetType var = new TargetType();
            if (n.getVariables().size() == 1) {
                VariableDeclarator var = n.getVariables().get(0);
                if (var.getType().toString().equals("Flyway") || 
                    var.getType().toString().equals(TARGET_CLASS)) {
                    
                    Optional<Expression> initializer = var.getInitializer();
                    if (initializer.isPresent() && 
                        initializer.get() instanceof ObjectCreationExpr) {
                        
                        ObjectCreationExpr creation = (ObjectCreationExpr) initializer.get();
                        if (creation.getArguments().isEmpty() && 
                            (creation.getType().toString().equals("Flyway") || 
                             creation.getType().toString().equals(TARGET_CLASS))) {
                            
                            // Found target variable declaration with no-arg constructor
                            String varName = var.getNameAsString();
                            targetVariables.put(varName, new ArrayList<>());
                            transformed = true;
                            // Remove this variable declaration
                            return null;
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Look for setter calls on target variables
            if (n.getNameAsString().startsWith("set") && n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String varName = ((NameExpr) scope).getNameAsString();
                    if (targetVariables.containsKey(varName)) {
                        // Check for special setters
                        if (n.getNameAsString().equals(SPECIAL_SETTER) && 
                            n.getArguments().size() == 1) {
                            specialArguments.put(varName, n.getArgument(0).clone());
                        } else {
                            // Record this setter call for later transformation
                            targetVariables.get(varName).add(n.clone());
                        }
                        transformed = true;
                        // Remove the original statement
                        return null;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            // Look for return statements returning target variables
            if (n.getExpression().isPresent()) {
                Expression expr = n.getExpression().get();
                if (expr instanceof NameExpr) {
                    String varName = ((NameExpr) expr).getNameAsString();
                    if (targetVariables.containsKey(varName)) {
                        // Store this return statement for transformation
                        variableReturns.put(varName, n);
                        transformed = true;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(BlockStmt n, Void arg) {
            // After visiting children, check if we need to transform patterns
            super.visit(n, arg);
            
            if (!targetVariables.isEmpty()) {
                // Transform each target variable pattern
                for (Map.Entry<String, List<MethodCallExpr>> entry : targetVariables.entrySet()) {
                    String varName = entry.getKey();
                    List<MethodCallExpr> setters = entry.getValue();
                    ReturnStmt returnStmt = variableReturns.get(varName);
                    Expression specialArg = specialArguments.get(varName);
                    
                    if (returnStmt != null && (!setters.isEmpty() || specialArg != null)) {
                        // Build the new fluent API chain
                        MethodCallExpr configureCall;
                        if (specialArg != null) {
                            configureCall = new MethodCallExpr(
                                new NameExpr("Flyway"), CONFIGURE_METHOD, 
                                new NodeList<>(specialArg));
                        } else {
                            configureCall = new MethodCallExpr(
                                new NameExpr("Flyway"), CONFIGURE_METHOD);
                        }
                        
                        // Transform each setter to fluent method
                        MethodCallExpr chain = configureCall;
                        for (MethodCallExpr setter : setters) {
                            String methodName = setter.getNameAsString();
                            String fluentMethod = methodName.substring(3); // Remove "set"
                            fluentMethod = Character.toLowerCase(fluentMethod.charAt(0)) + 
                                          fluentMethod.substring(1);
                            
                            MethodCallExpr newCall = new MethodCallExpr(chain, fluentMethod);
                            for (Expression argument : setter.getArguments()) {
                                newCall.addArgument(argument.clone());
                            }
                            chain = newCall;
                        }
                        
                        // Add .load() at the end
                        MethodCallExpr loadCall = new MethodCallExpr(chain, LOAD_METHOD);
                        
                        // Replace the return statement
                        returnStmt.setExpression(loadCall);
                    }
                }
                
                // Clear for next block
                targetVariables.clear();
                variableReturns.clear();
                specialArguments.clear();
            }
            
            return n;
        }
        
        public boolean wasTransformed() {
            return transformed;
        }
    }
}