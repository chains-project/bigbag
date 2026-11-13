package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Generic transformation rule for Flyway API migration from versions < 9.x to >= 9.x.
 * 
 * Breaking Change: Flyway no longer has a no-arg constructor and fluent setters.
 * New API requires using Flyway.configure() to create a FluentConfiguration,
 * then chaining configuration methods, ending with .load().
 * 
 * Transformation pattern:
 *   OLD: new Flyway().setX(a).setY(b)
 *   NEW: Flyway.configure().x(a).y(b).load()
 *   
 *   OLD: Flyway f = new Flyway(); f.setX(a); f.setY(b); return f;
 *   NEW: return Flyway.configure().x(a).y(b).load();
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        try {
            int transformedFiles = transformFlywayApi(sourceDir);
            System.out.println("Transformation completed. Modified " + transformedFiles + " file(s).");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformFlywayApi(String sourceDir) throws IOException {
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedCount = 0;
        for (Path file : javaFiles) {
            if (transformFileWithJavaParser(file)) {
                transformedCount++;
            }
        }
        return transformedCount;
    }
    
    private static boolean transformFileWithJavaParser(Path file) throws IOException {
        try {
            ParseResult<CompilationUnit> parseResult = new JavaParser().parse(file);
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                return false;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            FlywayTransformer transformer = new FlywayTransformer();
            CompilationUnit transformed = transformer.transform(cu);
            
            if (transformer.wasModified()) {
                Files.write(file, transformed.toString().getBytes());
                System.out.println("  Modified: " + file);
                return true;
            }
        } catch (Exception e) {
            System.err.println("  Error parsing " + file + ": " + e.getMessage());
        }
        return false;
    }
    
    private static class FlywayTransformer {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        public CompilationUnit transform(CompilationUnit cu) {
            // First pass: collect Flyway variable declarations
            Map<String, List<MethodCallExpr>> variableSetters = new HashMap<>();
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(VariableDeclarationExpr n, Void arg) {
                    for (VariableDeclarator vd : n.getVariables()) {
                        if (isFlywayType(vd.getType())) {
                            variableSetters.put(vd.getNameAsString(), new ArrayList<>());
                        }
                    }
                    super.visit(n, arg);
                }
            }, null);
            
            // Second pass: transform
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(ObjectCreationExpr n, Void arg) {
                    if (isFlywayCreation(n)) {
                        return transformFlywayCreation(n);
                    }
                    return super.visit(n, arg);
                }
                
                @Override
                public Visitable visit(MethodCallExpr n, Void arg) {
                    // Check for setter calls on Flyway variables
                    Optional<Expression> scope = n.getScope();
                    if (scope.isPresent()) {
                        Expression scopeExpr = scope.get();
                        if (scopeExpr instanceof NameExpr) {
                            String varName = ((NameExpr) scopeExpr).getNameAsString();
                            if (variableSetters.containsKey(varName) && 
                                n.getNameAsString().startsWith("set")) {
                                // Track or transform based on context
                                variableSetters.get(varName).add(n.clone());
                            }
                        } else if (scopeExpr instanceof ObjectCreationExpr) {
                            ObjectCreationExpr objCreation = (ObjectCreationExpr) scopeExpr;
                            if (isFlywayCreation(objCreation)) {
                                // Inline chain: new Flyway().setX(...)
                                return transformInlineChain(objCreation, n);
                            }
                        }
                    }
                    return super.visit(n, arg);
                }
                
                @Override
                public Visitable visit(ReturnStmt n, Void arg) {
                    if (n.getExpression().isPresent()) {
                        Expression expr = n.getExpression().get();
                        if (expr instanceof NameExpr) {
                            String varName = ((NameExpr) expr).getNameAsString();
                            if (variableSetters.containsKey(varName)) {
                                List<MethodCallExpr> setters = variableSetters.get(varName);
                                if (!setters.isEmpty()) {
                                    // Transform return f; to return Flyway.configure()...load();
                                    n.setExpression(buildFluentConfiguration(setters));
                                    modified = true;
                                }
                            }
                        }
                    }
                    return super.visit(n, arg);
                }
            }, null);
            
            return cu;
        }
        
        private boolean isFlywayType(Type type) {
            if (type.isClassOrInterfaceType()) {
                ClassOrInterfaceType classType = type.asClassOrInterfaceType();
                return classType.getNameAsString().equals("Flyway");
            }
            return false;
        }
        
        private boolean isFlywayCreation(ObjectCreationExpr n) {
            return isFlywayType(n.getType()) && n.getArguments().isEmpty();
        }
        
        private Node transformFlywayCreation(ObjectCreationExpr n) {
            // Just new Flyway() by itself - replace with Flyway.configure()
            NameExpr flywayClass = new NameExpr("Flyway");
            MethodCallExpr configureCall = new MethodCallExpr(flywayClass, "configure");
            modified = true;
            return configureCall;
        }
        
        private Node transformInlineChain(ObjectCreationExpr objCreation, MethodCallExpr methodCall) {
            // Collect the entire chain
            List<MethodCallExpr> chain = extractMethodChain(methodCall);
            
            // Build fluent configuration
            MethodCallExpr configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            Expression currentExpr = configureCall;
            
            for (MethodCallExpr setter : chain) {
                currentExpr = buildFluentMethodCall(currentExpr, setter);
            }
            
            MethodCallExpr loadCall = new MethodCallExpr(currentExpr, "load");
            modified = true;
            return loadCall;
        }
        
        private List<MethodCallExpr> extractMethodChain(MethodCallExpr methodCall) {
            List<MethodCallExpr> chain = new ArrayList<>();
            MethodCallExpr current = methodCall;
            
            while (true) {
                chain.add(0, current.clone());
                Optional<Expression> scope = current.getScope();
                if (scope.isPresent() && scope.get() instanceof MethodCallExpr) {
                    current = (MethodCallExpr) scope.get();
                } else {
                    break;
                }
            }
            return chain;
        }
        
        private Expression buildFluentConfiguration(List<MethodCallExpr> setters) {
            MethodCallExpr configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            Expression currentExpr = configureCall;
            
            for (MethodCallExpr setter : setters) {
                currentExpr = buildFluentMethodCall(currentExpr, setter);
            }
            
            return new MethodCallExpr(currentExpr, "load");
        }
        
        private MethodCallExpr buildFluentMethodCall(Expression target, MethodCallExpr setter) {
            String oldMethodName = setter.getNameAsString();
            String newMethodName = mapMethodName(oldMethodName);
            return new MethodCallExpr(target, newMethodName, setter.getArguments());
        }
        
        private String mapMethodName(String oldName) {
            switch (oldName) {
                case "setDataSource": return "dataSource";
                case "setClassLoader": return "classLoader";
                case "setLocations": return "locations";
                case "setValidateOnMigrate": return "validateOnMigrate";
                case "setBaselineOnMigrate": return "baselineOnMigrate";
                case "setBaselineVersion": return "baselineVersion";
                case "setTable": return "table";
                case "setEncoding": return "encoding";
                case "setSchemas": return "schemas";
                case "setPlaceholderPrefix": return "placeholderPrefix";
                case "setPlaceholderSuffix": return "placeholderSuffix";
                case "setPlaceholderReplacement": return "placeholderReplacement";
                case "setPlaceholders": return "placeholders";
                case "setSqlMigrationPrefix": return "sqlMigrationPrefix";
                case "setRepeatableSqlMigrationPrefix": return "repeatableSqlMigrationPrefix";
                case "setSqlMigrationSeparator": return "sqlMigrationSeparator";
                case "setSqlMigrationSuffixes": return "sqlMigrationSuffixes";
                default:
                    // For unknown setters, try removing "set" prefix and lowercasing first letter
                    if (oldName.startsWith("set") && oldName.length() > 3) {
                        return Character.toLowerCase(oldName.charAt(3)) + oldName.substring(4);
                    }
                    return oldName;
            }
        }
    }
}