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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Generic transformation rule for Flyway API breaking change from version <9 to >=9.
 * 
 * Transforms old pattern:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setClassLoader(classLoader);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validate);
 *   return flyway;
 * 
 * To new pattern:
 *   return Flyway.configure(classLoader)
 *               .dataSource(dataSource)
 *               .locations(locations)
 *               .validateOnMigrate(validate)
 *               .load();
 * 
 * This is a generic, reusable transformation that can be applied to any project
 * affected by the Flyway 9+ API breaking change.
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
            transformDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(Path directory) throws Exception {
        Files.walk(directory)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> transformFile(p.toFile()));
    }
    
    private static void transformFile(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser javaParser = new JavaParser();
            CompilationUnit cu = javaParser.parse(in).getResult().orElseThrow();
            
            FlywayTransformer transformer = new FlywayTransformer();
            CompilationUnit modifiedCu = (CompilationUnit) transformer.visit(cu, null);
            
            if (transformer.isModified()) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(modifiedCu.toString().getBytes());
                    System.out.println("Transformed: " + file.getPath());
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + file.getPath() + ": " + e.getMessage());
        }
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(MethodDeclaration n, Void arg) {
            MethodDeclaration method = (MethodDeclaration) super.visit(n, arg);
            BlockStmt body = method.getBody().orElse(null);
            if (body == null) return method;
            
            // Scan for Flyway object creation patterns
            List<Statement> statements = new ArrayList<>(body.getStatements());
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (containsFlywayCreation(stmt)) {
                    // Found a Flyway creation, try to transform it
                    if (transformFlywayBlock(method, body, i)) {
                        modified = true;
                        // Restart scanning since we modified the statements
                        return visit(method, arg);
                    }
                }
            }
            
            return method;
        }
        
        private boolean containsFlywayCreation(Statement stmt) {
            if (stmt.isExpressionStmt()) {
                Expression expr = stmt.asExpressionStmt().getExpression();
                if (expr.isVariableDeclarationExpr()) {
                    VariableDeclarationExpr varDecl = expr.asVariableDeclarationExpr();
                    for (VariableDeclarator var : varDecl.getVariables()) {
                        String typeName = var.getType().asString();
                        if ((typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway")) && 
                            var.getInitializer().isPresent() &&
                            var.getInitializer().get().isObjectCreationExpr()) {
                            ObjectCreationExpr creation = var.getInitializer().get().asObjectCreationExpr();
                            String creationType = creation.getType().asString();
                            if ((creationType.equals("Flyway") || creationType.equals("org.flywaydb.core.Flyway")) && 
                                creation.getArguments().isEmpty()) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
        
        private boolean transformFlywayBlock(MethodDeclaration method, BlockStmt body, int creationIndex) {
            List<Statement> statements = body.getStatements();
            if (creationIndex >= statements.size()) return false;
            
            Statement creationStmt = statements.get(creationIndex);
            if (!creationStmt.isExpressionStmt()) return false;
            
            Expression expr = creationStmt.asExpressionStmt().getExpression();
            if (!expr.isVariableDeclarationExpr()) return false;
            
            VariableDeclarationExpr varDecl = expr.asVariableDeclarationExpr();
            VariableDeclarator flywayVar = null;
            for (VariableDeclarator var : varDecl.getVariables()) {
                if (var.getType().asString().equals("Flyway") && 
                    var.getInitializer().isPresent() &&
                    var.getInitializer().get().isObjectCreationExpr()) {
                    ObjectCreationExpr creation = var.getInitializer().get().asObjectCreationExpr();
                    if (creation.getType().asString().equals("Flyway") && 
                        creation.getArguments().isEmpty()) {
                        flywayVar = var;
                        break;
                    }
                }
            }
            
            if (flywayVar == null) return false;
            String varName = flywayVar.getNameAsString();
            
            // Collect setter calls for this variable
            List<MethodCallExpr> setterCalls = new ArrayList<>();
            Expression classLoaderArg = null;
            
            for (int i = creationIndex + 1; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (stmt.isExpressionStmt()) {
                    ExpressionStmt exprStmt = stmt.asExpressionStmt();
                    if (exprStmt.getExpression().isMethodCallExpr()) {
                        MethodCallExpr call = exprStmt.getExpression().asMethodCallExpr();
                        if (isFlywaySetterCall(call, varName)) {
                            setterCalls.add(call);
                            if ("setClassLoader".equals(call.getNameAsString()) && 
                                !call.getArguments().isEmpty()) {
                                classLoaderArg = call.getArguments().get(0);
                            }
                        }
                    }
                }
            }
            
            if (setterCalls.isEmpty()) return false;
            
            // Find the return statement that returns this variable
            ReturnStmt returnStmt = null;
            for (int i = creationIndex + setterCalls.size(); i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (stmt.isReturnStmt()) {
                    ReturnStmt rs = stmt.asReturnStmt();
                    if (rs.getExpression().isPresent() && 
                        rs.getExpression().get().isNameExpr()) {
                        NameExpr nameExpr = rs.getExpression().get().asNameExpr();
                        if (nameExpr.getNameAsString().equals(varName)) {
                            returnStmt = rs;
                            break;
                        }
                    }
                }
            }
            
            if (returnStmt == null) return false;
            
            // Build the new Flyway configuration expression
            MethodCallExpr configureCall;
            if (classLoaderArg != null) {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure", 
                    new NodeList<>(classLoaderArg));
            } else {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            }
            
            // Apply setters as fluent calls
            MethodCallExpr currentCall = configureCall;
            for (MethodCallExpr setter : setterCalls) {
                String oldMethod = setter.getNameAsString();
                String newMethod = mapMethodName(oldMethod);
                
                if (newMethod != null && !"setClassLoader".equals(oldMethod)) {
                    NodeList<Expression> args = setter.getArguments();
                    currentCall = new MethodCallExpr(currentCall, newMethod, args);
                }
            }
            
            // Add .load() at the end
            MethodCallExpr loadCall = new MethodCallExpr(currentCall, "load");
            
            // Replace the return statement with the new expression
            returnStmt.setExpression(loadCall);
            
            // Remove the creation statement and setter calls
            creationStmt.remove();
            for (MethodCallExpr setter : setterCalls) {
                Node parent = setter.getParentNode().orElse(null);
                if (parent != null) {
                    Node grandParent = parent.getParentNode().orElse(null);
                    if (grandParent instanceof ExpressionStmt) {
                        ((ExpressionStmt) grandParent).remove();
                    }
                }
            }
            
            return true;
        }
        
        private boolean isFlywaySetterCall(MethodCallExpr call, String varName) {
            if (!call.getScope().isPresent()) return false;
            
            Expression scope = call.getScope().get();
            if (!scope.isNameExpr()) return false;
            
            String scopeName = scope.asNameExpr().getNameAsString();
            if (!scopeName.equals(varName)) return false;
            
            String methodName = call.getNameAsString();
            return methodName.equals("setDataSource") || 
                   methodName.equals("setClassLoader") ||
                   methodName.equals("setLocations") ||
                   methodName.equals("setValidateOnMigrate");
        }
        
        private String mapMethodName(String oldMethod) {
            switch (oldMethod) {
                case "setDataSource": return "dataSource";
                case "setLocations": return "locations";
                case "setValidateOnMigrate": return "validateOnMigrate";
                default: return null;
            }
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}