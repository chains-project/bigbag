package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                FlywayTransformer transformer = new FlywayTransformer();
                CompilationUnit transformed = (CompilationUnit) cu.accept(transformer, null);
                
                if (transformer.wasTransformed()) {
                    try (FileOutputStream out = new FileOutputStream(javaFile.toFile())) {
                        out.write(transformed.toString().getBytes());
                    }
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public Visitable visit(BlockStmt n, Void arg) {
            // First visit children
            n = (BlockStmt) super.visit(n, arg);
            
            // Look for the pattern: new Flyway() followed by setters then return
            for (int i = 0; i < n.getStatements().size(); i++) {
                Node stmt = n.getStatements().get(i);
                
                if (stmt instanceof ExpressionStmt) {
                    ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                    Expression expr = exprStmt.getExpression();
                    
                    // Check for: final Flyway flyway = new Flyway();
                    if (expr instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                        
                        for (var variable : varDecl.getVariables()) {
                            if (variable.getInitializer().isPresent()) {
                                Expression init = variable.getInitializer().get();
                                
                                // Check if it's new Flyway()
                                if (isFlywayConstructor(init)) {
                                    String varName = variable.getNameAsString();
                                    
                                    // Look ahead for setters and return statement
                                    List<MethodCallExpr> setters = new ArrayList<>();
                                    int returnIdx = -1;
                                    ReturnStmt returnStmt = null;
                                    
                                    for (int j = i + 1; j < n.getStatements().size(); j++) {
                                        Node nextStmt = n.getStatements().get(j);
                                        
                                        if (nextStmt instanceof ExpressionStmt) {
                                            ExpressionStmt nextExprStmt = (ExpressionStmt) nextStmt;
                                            Expression nextExpr = nextExprStmt.getExpression();
                                            
                                            // Check for setter calls on this variable
                                            if (nextExpr instanceof MethodCallExpr) {
                                                MethodCallExpr methodCall = (MethodCallExpr) nextExpr;
                                                if (methodCall.getScope().isPresent()) {
                                                    Expression scope = methodCall.getScope().get();
                                                    if (scope instanceof NameExpr) {
                                                        String scopeName = ((NameExpr) scope).getNameAsString();
                                                        if (scopeName.equals(varName) && 
                                                            methodCall.getNameAsString().startsWith("set")) {
                                                            setters.add(methodCall);
                                                            continue;
                                                        }
                                                    }
                                                }
                                            }
                                        } else if (nextStmt instanceof ReturnStmt) {
                                            returnStmt = (ReturnStmt) nextStmt;
                                            if (returnStmt.getExpression().isPresent()) {
                                                Expression retExpr = returnStmt.getExpression().get();
                                                if (retExpr instanceof NameExpr) {
                                                    String retVar = ((NameExpr) retExpr).getNameAsString();
                                                    if (retVar.equals(varName)) {
                                                        returnIdx = j;
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        
                                        // If we reach here, pattern is broken
                                        break;
                                    }
                                    
                                    // If we found the full pattern, transform it
                                    if (returnIdx != -1 && !setters.isEmpty()) {
                                        transformed = true;
                                        
                                        // Build the fluent API chain
                                        MethodCallExpr configureCall = new MethodCallExpr(
                                            new NameExpr("Flyway"), "configure");
                                        
                                        MethodCallExpr chain = configureCall;
                                        for (MethodCallExpr setter : setters) {
                                            String setterName = setter.getNameAsString();
                                            String fluentName = getFluentMethodName(setterName);
                                            if (fluentName != null) {
                                                MethodCallExpr fluentCall = new MethodCallExpr(chain, fluentName);
                                                setter.getArguments().forEach(fluentCall::addArgument);
                                                chain = fluentCall;
                                            }
                                        }
                                        
                                        // Add .load() at the end
                                        MethodCallExpr loadCall = new MethodCallExpr(chain, "load");
                                        returnStmt.setExpression(loadCall);
                                        
                                        // Remove the variable declaration and setters
                                        n.getStatements().remove(i);
                                        for (int j = 0; j < setters.size(); j++) {
                                            n.getStatements().remove(i); // After removal, indices shift
                                        }
                                        
                                        // Adjust loop counter since we modified the list
                                        i--;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return n;
        }
        
        private boolean isFlywayConstructor(Expression expr) {
            if (expr instanceof ObjectCreationExpr) {
                ObjectCreationExpr creation = (ObjectCreationExpr) expr;
                String typeName = creation.getType().toString();
                return (typeName.equals("Flyway") || 
                        typeName.equals("org.flywaydb.core.Flyway")) &&
                       creation.getArguments().isEmpty();
            }
            return false;
        }
        
        private String getFluentMethodName(String setterName) {
            Map<String, String> methodMap = new HashMap<>();
            methodMap.put("setDataSource", "dataSource");
            methodMap.put("setClassLoader", "classLoader");
            methodMap.put("setLocations", "locations");
            methodMap.put("setValidateOnMigrate", "validateOnMigrate");
            methodMap.put("setBaselineVersion", "baselineVersion");
            methodMap.put("setBaselineDescription", "baselineDescription");
            methodMap.put("setBaselineOnMigrate", "baselineOnMigrate");
            methodMap.put("setPlaceholders", "placeholders");
            methodMap.put("setPlaceholderPrefix", "placeholderPrefix");
            methodMap.put("setPlaceholderSuffix", "placeholderSuffix");
            methodMap.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
            methodMap.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
            methodMap.put("setTable", "table");
            methodMap.put("setTarget", "target");
            methodMap.put("setOutOfOrder", "outOfOrder");
            methodMap.put("setIgnoreMissingMigrations", "ignoreMissingMigrations");
            methodMap.put("setIgnoreFutureMigrations", "ignoreFutureMigrations");
            methodMap.put("setValidateMigrationNaming", "validateMigrationNaming");
            methodMap.put("setCleanOnValidationError", "cleanOnValidationError");
            methodMap.put("setCleanDisabled", "cleanDisabled");
            return methodMap.get(setterName);
        }
    }
}