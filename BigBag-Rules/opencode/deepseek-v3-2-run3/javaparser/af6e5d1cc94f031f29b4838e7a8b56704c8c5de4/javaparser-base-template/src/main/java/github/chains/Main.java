package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Flyway API migration from version < 9.17.0 to >= 9.17.0.
 * 
 * This transformation handles the breaking change where:
 * - Old API: new Flyway() followed by setter methods (setDataSource, setClassLoader, etc.)
 * - New API: Flyway.configure().fluentMethods().load()
 * 
 * The transformation is generic and can be applied to any Java project by specifying
 * the source directory path as the first argument.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Flyway API usage in all Java files in the directory");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                boolean fileModified = transformFile(javaFile);
                if (fileModified) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformation complete. Modified " + transformedFiles + " files.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            FlywayTransformer transformer = new FlywayTransformer();
            transformer.visit(cu, null);
            
            if (transformer.isModified()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Modified: " + filePath);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Visitor that transforms Flyway API usage from old pattern to new pattern.
     */
    private static class FlywayTransformer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        private JavaParser parser = new JavaParser();
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            
            if (n.getBody().isPresent()) {
                BlockStmt body = n.getBody().get();
                transformMethodBody(body);
            }
        }
        
        private void transformMethodBody(BlockStmt body) {
            for (int i = 0; i < body.getStatements().size(); i++) {
                Node stmt = body.getStatement(i);
                
                if (stmt instanceof ExpressionStmt) {
                    ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                    if (exprStmt.getExpression() instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) exprStmt.getExpression();
                        
                        if (isFlywayVariableDeclaration(varDecl)) {
                            String varName = varDecl.getVariable(0).getNameAsString();
                            List<MethodCallExpr> setters = findSettersForVariable(varName, body, i + 1);
                            
                            if (!setters.isEmpty()) {
                                ReturnStmt returnStmt = findReturnStatementForVariable(varName, body, i + 1);
                                
                                if (returnStmt != null) {
                                    transformFlywayCreation(varDecl, setters, returnStmt, body);
                                    modified = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        private boolean isFlywayVariableDeclaration(VariableDeclarationExpr varDecl) {
            if (varDecl.getVariable(0).getInitializer().isPresent()) {
                Expression init = varDecl.getVariable(0).getInitializer().get();
                if (init instanceof ObjectCreationExpr) {
                    ObjectCreationExpr oce = (ObjectCreationExpr) init;
                    return oce.getType().asString().equals("Flyway") && 
                           oce.getArguments().isEmpty();
                }
            }
            return false;
        }
        
        private List<MethodCallExpr> findSettersForVariable(String varName, BlockStmt body, int startIndex) {
            List<MethodCallExpr> setters = new ArrayList<>();
            
            for (int i = startIndex; i < body.getStatements().size(); i++) {
                Node stmt = body.getStatement(i);
                
                if (stmt instanceof ReturnStmt) {
                    break;
                }
                
                if (stmt instanceof ExpressionStmt) {
                    ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                    if (exprStmt.getExpression() instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) exprStmt.getExpression();
                        if (methodCall.getScope().isPresent()) {
                            Expression scope = methodCall.getScope().get();
                            if (scope instanceof NameExpr) {
                                NameExpr nameExpr = (NameExpr) scope;
                                if (nameExpr.getNameAsString().equals(varName)) {
                                    String methodName = methodCall.getNameAsString();
                                    if (isFlywaySetterMethod(methodName)) {
                                        setters.add(methodCall);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return setters;
        }
        
        private boolean isFlywaySetterMethod(String methodName) {
            return methodName.startsWith("set") && (
                methodName.equals("setDataSource") ||
                methodName.equals("setClassLoader") ||
                methodName.equals("setLocations") ||
                methodName.equals("setValidateOnMigrate") ||
                methodName.equals("setLocationsAsStrings")
            );
        }
        
        private ReturnStmt findReturnStatementForVariable(String varName, BlockStmt body, int startIndex) {
            for (int i = startIndex; i < body.getStatements().size(); i++) {
                Node stmt = body.getStatement(i);
                
                if (stmt instanceof ReturnStmt) {
                    ReturnStmt returnStmt = (ReturnStmt) stmt;
                    if (returnStmt.getExpression().isPresent()) {
                        Expression returnExpr = returnStmt.getExpression().get();
                        if (returnExpr instanceof NameExpr) {
                            NameExpr nameExpr = (NameExpr) returnExpr;
                            if (nameExpr.getNameAsString().equals(varName)) {
                                return returnStmt;
                            }
                        }
                    }
                }
            }
            
            return null;
        }
        
        private void transformFlywayCreation(VariableDeclarationExpr varDecl, 
                                           List<MethodCallExpr> setters,
                                           ReturnStmt returnStmt,
                                           BlockStmt body) {
            StringBuilder newExpr = new StringBuilder();
            
            // Check if there's a setClassLoader call - it should go in configure()
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                if (setter.getNameAsString().equals("setClassLoader")) {
                    classLoaderArg = setter.getArgument(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            if (classLoaderArg != null) {
                newExpr.append("Flyway.configure(").append(classLoaderArg.toString()).append(")");
            } else {
                newExpr.append("Flyway.configure()");
            }
            
            for (MethodCallExpr setter : otherSetters) {
                String methodName = setter.getNameAsString();
                String fluentMethodName = convertSetterToFluent(methodName);
                
                newExpr.append(".").append(fluentMethodName).append("(");
                
                for (int i = 0; i < setter.getArguments().size(); i++) {
                    if (i > 0) newExpr.append(", ");
                    newExpr.append(setter.getArgument(i).toString());
                }
                
                newExpr.append(")");
            }
            
            newExpr.append(".load()");
            
            Expression newFlywayExpr = parser.parseExpression(newExpr.toString()).getResult().get();
            returnStmt.setExpression(newFlywayExpr);
            
            Node varDeclParent = varDecl.getParentNode().orElse(null);
            if (varDeclParent != null) {
                body.getStatements().remove(varDeclParent);
            }
            
            for (MethodCallExpr setter : setters) {
                Node setterParent = setter.getParentNode().orElse(null);
                if (setterParent != null) {
                    body.getStatements().remove(setterParent);
                }
            }
        }
        
        private String convertSetterToFluent(String setterName) {
            if (setterName.equals("setLocationsAsStrings")) {
                return "locations";
            }
            
            String baseName = setterName.substring(3);
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        }
    }
}