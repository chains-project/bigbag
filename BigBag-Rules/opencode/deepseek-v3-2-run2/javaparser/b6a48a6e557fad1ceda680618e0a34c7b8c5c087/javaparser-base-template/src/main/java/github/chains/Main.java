package github.chains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformed = 0;
        for (Path file : javaFiles) {
            if (transformFile(file)) {
                transformed++;
            }
        }
        
        System.out.println("Transformed " + transformed + " files");
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path file) throws IOException {
        String content = Files.readString(file);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        // Check if this file imports Flyway
        boolean hasFlywayImport = cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("org.flywaydb.core.Flyway"));
        
        if (!hasFlywayImport) {
            return false;
        }
        
        FlywayTransformer transformer = new FlywayTransformer();
        cu.accept(transformer, null);
        
        if (transformer.wasModified()) {
            Files.writeString(file, cu.toString());
            System.out.println("Transformed: " + file);
            return true;
        }
        
        return false;
    }
    
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Process method body for Flyway patterns
            if (n.getBody().isPresent()) {
                BlockStmt body = n.getBody().get();
                List<Statement> statements = new ArrayList<>(body.getStatements());
                
                // Look for Flyway creation patterns
                for (int i = 0; i < statements.size(); i++) {
                    Statement stmt = statements.get(i);
                    
                    // Pattern 1: Variable declaration with new Flyway()
                    if (stmt instanceof ExpressionStmt) {
                        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                        if (exprStmt.getExpression() instanceof VariableDeclarationExpr) {
                            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) exprStmt.getExpression();
                            for (VariableDeclarator var : varDecl.getVariables()) {
                                if (var.getInitializer().isPresent() && 
                                    var.getInitializer().get() instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creation = (ObjectCreationExpr) var.getInitializer().get();
                                    if (isFlywayCreation(creation)) {
                                        String varName = var.getNameAsString();
                                        List<MethodCallExpr> setters = findSettersForVariable(body, varName, i + 1);
                                        
                                        if (!setters.isEmpty()) {
                                            // Transform this pattern
                                            transformFlywayDeclaration(varDecl, var, creation, setters, body, i);
                                            modified = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isFlywayCreation(ObjectCreationExpr creation) {
            // Check if this is creating a Flyway instance
            // Type could be just "Flyway" or fully qualified
            String typeName = creation.getType().asString();
            return typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway");
        }
        
        private List<MethodCallExpr> findSettersForVariable(BlockStmt body, String varName, int startIndex) {
            List<MethodCallExpr> setters = new ArrayList<>();
            List<Statement> statements = body.getStatements();
            
            for (int i = startIndex; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (stmt instanceof ExpressionStmt) {
                    ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                    if (exprStmt.getExpression() instanceof MethodCallExpr) {
                        MethodCallExpr call = (MethodCallExpr) exprStmt.getExpression();
                        if (isSetterCallOnVariable(call, varName)) {
                            setters.add(call);
                        } else {
                            // If we hit a non-setter statement, stop looking
                            // (setters should be contiguous after declaration)
                            break;
                        }
                    } else {
                        // Non-method-call statement, stop looking
                        break;
                    }
                } else {
                    // Non-expression statement, stop looking
                    break;
                }
            }
            
            return setters;
        }
        
        private boolean isSetterCallOnVariable(MethodCallExpr call, String varName) {
            if (!call.getNameAsString().startsWith("set")) {
                return false;
            }
            
            if (call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (scope instanceof NameExpr) {
                    return ((NameExpr) scope).getNameAsString().equals(varName);
                }
            }
            
            return false;
        }
        
        private void transformFlywayDeclaration(VariableDeclarationExpr varDecl, 
                                               VariableDeclarator var, 
                                               ObjectCreationExpr creation,
                                               List<MethodCallExpr> setters,
                                               BlockStmt body,
                                               int declIndex) {
            // Handle ClassLoader specially - it should be passed to configure() not as fluent method
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                if (setter.getNameAsString().equals("setClassLoader")) {
                    classLoaderArg = setter.getArguments().get(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            // Create the new fluent builder expression
            MethodCallExpr configureCall;
            if (classLoaderArg != null) {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
                configureCall.setArguments(NodeList.nodeList(classLoaderArg));
            } else {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            }
            
            // Build the fluent chain with remaining setters
            MethodCallExpr currentBuilder = configureCall;
            for (MethodCallExpr setter : otherSetters) {
                String setterName = setter.getNameAsString();
                String fluentMethod = convertSetterToFluent(setterName);
                
                // Special handling for certain setter methods
                if (setterName.equals("setLocations") && setter.getArguments().size() == 1) {
                    // Single string argument needs to be converted to varargs
                    MethodCallExpr fluentCall = new MethodCallExpr(currentBuilder, "locations");
                    fluentCall.setArguments(setter.getArguments());
                    currentBuilder = fluentCall;
                } else if (setterName.equals("setValidateOnMigrate")) {
                    MethodCallExpr fluentCall = new MethodCallExpr(currentBuilder, "validateOnMigrate");
                    // Handle Boolean conversion
                    NodeList<Expression> args = setter.getArguments();
                    if (args.size() == 1) {
                        Expression arg = args.get(0);
                        // Check if it's Boolean.valueOf(...)
                        if (arg instanceof MethodCallExpr) {
                            MethodCallExpr methodCall = (MethodCallExpr) arg;
                            if (methodCall.getNameAsString().equals("valueOf") && 
                                methodCall.getScope().isPresent() &&
                                methodCall.getScope().get() instanceof NameExpr &&
                                ((NameExpr)methodCall.getScope().get()).getNameAsString().equals("Boolean")) {
                                // Keep Boolean.valueOf(...) - new API accepts Boolean
                                fluentCall.setArguments(args);
                            } else {
                                fluentCall.setArguments(args);
                            }
                        } else {
                            fluentCall.setArguments(args);
                        }
                    } else {
                        fluentCall.setArguments(args);
                    }
                    currentBuilder = fluentCall;
                } else if (setterName.equals("setDataSource")) {
                    // dataSource method in fluent API
                    MethodCallExpr fluentCall = new MethodCallExpr(currentBuilder, "dataSource");
                    fluentCall.setArguments(setter.getArguments());
                    currentBuilder = fluentCall;
                } else {
                    // Generic setter conversion
                    MethodCallExpr fluentCall = new MethodCallExpr(currentBuilder, fluentMethod);
                    fluentCall.setArguments(setter.getArguments());
                    currentBuilder = fluentCall;
                }
            }
            
            // Add .load() to create the Flyway instance
            MethodCallExpr loadCall = new MethodCallExpr(currentBuilder, "load");
            
            // Replace the initializer in the variable declaration
            var.setInitializer(loadCall);
            
            // Remove all setter statements (both ClassLoader and others)
            List<Statement> statements = body.getStatements();
            // We need to remove all setter statements
            // They should be contiguous after the declaration
            int settersToRemove = setters.size();
            for (int i = 0; i < settersToRemove; i++) {
                int removeIndex = declIndex + 1; // Setters come right after declaration
                if (removeIndex < statements.size()) {
                    statements.remove(removeIndex);
                }
            }
        }
        
        private String convertSetterToFluent(String setterName) {
            // Convert setXyz to xyz (camelCase)
            String baseName = setterName.substring(3);
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}