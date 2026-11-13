package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws Exception {
        Files.walk(directory)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> processFile(p.toFile()));
    }
    
    private static void processFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(in).getResult().orElseThrow();
            
            FlywayTransformer transformer = new FlywayTransformer();
            CompilationUnit modifiedCu = (CompilationUnit) transformer.visit(cu, null);
            
            if (transformer.isModified()) {
                System.out.println("Modified file: " + file.getPath());
                Files.write(file.toPath(), modifiedCu.toString().getBytes());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + file.getPath() + ": " + e.getMessage());
        }
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            n = (MethodDeclaration) super.visit(n, arg);
            
            if (n.getBody().isPresent()) {
                BlockStmt body = n.getBody().get();
                List<Statement> newStatements = new ArrayList<>();
                List<Statement> statements = body.getStatements();
                
                for (int i = 0; i < statements.size(); i++) {
                    Statement stmt = statements.get(i);
                    
                    if (stmt instanceof ExpressionStmt) {
                        Expression expr = ((ExpressionStmt) stmt).getExpression();
                        
                        if (expr instanceof VariableDeclarationExpr) {
                            VariableDeclarationExpr varDeclExpr = (VariableDeclarationExpr) expr;
                            boolean isFlywayCreation = false;
                            String flywayVarName = null;
                            ObjectCreationExpr flywayCreation = null;
                            
                            for (VariableDeclarator varDecl : varDeclExpr.getVariables()) {
                                if (varDecl.getInitializer().isPresent() && 
                                    varDecl.getInitializer().get() instanceof ObjectCreationExpr) {
                                    
                                    ObjectCreationExpr creation = (ObjectCreationExpr) varDecl.getInitializer().get();
                                    if (creation.getType().asString().equals("Flyway") || 
                                        creation.getType().asString().equals("org.flywaydb.core.Flyway")) {
                                        
                                        isFlywayCreation = true;
                                        flywayVarName = varDecl.getNameAsString();
                                        flywayCreation = creation;
                                        break;
                                    }
                                }
                            }
                            
                            if (isFlywayCreation && flywayVarName != null && flywayCreation != null) {
                                List<MethodCallExpr> setterCalls = new ArrayList<>();
                                
                                for (int j = i + 1; j < statements.size(); j++) {
                                    Statement nextStmt = statements.get(j);
                                    if (nextStmt instanceof ExpressionStmt) {
                                        Expression nextExpr = ((ExpressionStmt) nextStmt).getExpression();
                                        if (nextExpr instanceof MethodCallExpr) {
                                            MethodCallExpr methodCall = (MethodCallExpr) nextExpr;
                                            if (isFlywaySetterCall(methodCall, flywayVarName)) {
                                                setterCalls.add(methodCall);
                                            } else {
                                                break;
                                            }
                                        } else {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                                
                                if (!setterCalls.isEmpty()) {
                                    MethodCallExpr configureCall = new MethodCallExpr(
                                        new NameExpr("Flyway"), "configure", new NodeList<>()
                                    );
                                    
                                    MethodCallExpr builderChain = configureCall;
                                    
                                    for (MethodCallExpr setter : setterCalls) {
                                        String methodName = setter.getNameAsString();
                                        String newMethodName = mapMethodName(methodName);
                                        
                                        if (newMethodName != null) {
                                            NodeList<Expression> args = setter.getArguments();
                                            MethodCallExpr newMethodCall = new MethodCallExpr(builderChain, newMethodName, args);
                                            builderChain = newMethodCall;
                                        }
                                    }
                                    
                                    MethodCallExpr loadCall = new MethodCallExpr(builderChain, "load", new NodeList<>());
                                    
                                    for (VariableDeclarator varDecl : varDeclExpr.getVariables()) {
                                        if (varDecl.getNameAsString().equals(flywayVarName)) {
                                            varDecl.setInitializer(loadCall);
                                            break;
                                        }
                                    }
                                    
                                    newStatements.add(stmt);
                                    i += setterCalls.size();
                                    modified = true;
                                    continue;
                                }
                            }
                        }
                    }
                    
                    newStatements.add(stmt);
                }
                
                if (modified) {
                    body.setStatements(new NodeList<>(newStatements));
                }
            }
            
            return n;
        }
        
        private boolean isFlywaySetterCall(MethodCallExpr expr, String varName) {
            if (!expr.getNameAsString().startsWith("set")) {
                return false;
            }
            
            if (expr.getScope().isPresent()) {
                Expression scope = expr.getScope().get();
                if (scope instanceof NameExpr) {
                    return ((NameExpr) scope).getNameAsString().equals(varName);
                }
            }
            
            return false;
        }
        
        private String mapMethodName(String oldMethodName) {
            if (oldMethodName.equals("setClassLoader")) {
                return null;
            }
            if (oldMethodName.startsWith("set")) {
                return oldMethodName.substring(3, 4).toLowerCase() + oldMethodName.substring(4);
            }
            return oldMethodName;
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}