package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    
    private static final String FLYWAY_CLASS = "org.flywaydb.core.Flyway";
    
    // Mapping from old setter methods to new fluent builder methods
    private static final Map<String, String> METHOD_MAPPING = Map.of(
        "setDataSource", "dataSource",
        "setClassLoader", "classLoader", 
        "setLocations", "locations",
        "setValidateOnMigrate", "validateOnMigrate"
    );
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser(new ParserConfiguration());
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            FlywayTransformer transformer = new FlywayTransformer();
            CompilationUnit transformed = (CompilationUnit) transformer.visit(cu, null);
            
            if (transformer.wasModified()) {
                Files.write(filePath, transformed.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Look for methods that contain Flyway constructor calls
            if (n.getBody().isPresent()) {
                BlockStmt body = n.getBody().get();
                List<ObjectCreationExpr> flywayConstructors = new ArrayList<>();
                
                // Find all Flyway constructor calls
                body.walk(ObjectCreationExpr.class, expr -> {
                    if (isFlywayConstructor(expr)) {
                        flywayConstructors.add(expr);
                    }
                });
                
                for (ObjectCreationExpr constructor : flywayConstructors) {
                    System.out.println("Found Flyway constructor in method: " + n.getNameAsString());
                    processFlywayConstructor(constructor, body);
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isFlywayConstructor(ObjectCreationExpr expr) {
            return expr.getType().asString().equals("Flyway") && 
                   expr.getArguments().isEmpty();
        }
        
        private void processFlywayConstructor(ObjectCreationExpr constructor, BlockStmt body) {
            // Find the variable declaration or assignment
            Optional<VariableDeclarationExpr> varDecl = constructor.findAncestor(VariableDeclarationExpr.class);
            Optional<AssignExpr> assignment = constructor.findAncestor(AssignExpr.class);
            
            String variableName = null;
            Node contextNode = null;
            
            if (varDecl.isPresent()) {
                VariableDeclarationExpr decl = varDecl.get();
                variableName = decl.getVariable(0).getNameAsString();
                contextNode = decl;
            } else if (assignment.isPresent()) {
                AssignExpr assign = assignment.get();
                if (assign.getTarget() instanceof NameExpr) {
                    variableName = ((NameExpr) assign.getTarget()).getNameAsString();
                    contextNode = assign;
                }
            }
            
            if (variableName != null && contextNode != null) {
                // Find all setter calls on this variable
                List<MethodCallExpr> setterCalls = findSetterCalls(body, variableName, contextNode);
                
                    if (!setterCalls.isEmpty()) {
                    System.out.println("Found " + setterCalls.size() + " setter calls for variable " + variableName);
                    for (MethodCallExpr call : setterCalls) {
                        System.out.println("  - " + call.getNameAsString());
                    }
                    modified = true;
                    
                    // Build the fluent configuration chain
                    MethodCallExpr configureCall = new MethodCallExpr(
                        new NameExpr("Flyway"), "configure");
                    
                    MethodCallExpr currentCall = configureCall;
                    for (MethodCallExpr setterCall : setterCalls) {
                        String oldMethodName = setterCall.getNameAsString();
                        String newMethodName = METHOD_MAPPING.get(oldMethodName);
                        
                        if (newMethodName != null) {
                            NodeList<Expression> arguments = setterCall.getArguments();
                            
                            currentCall = new MethodCallExpr(currentCall, newMethodName);
                            currentCall.setArguments(arguments);
                        }
                    }
                    
                    // Add the final load() call
                    MethodCallExpr loadCall = new MethodCallExpr(currentCall, "load");
                    
                    // Replace the original constructor with the fluent builder
                    if (contextNode instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr decl = (VariableDeclarationExpr) contextNode;
                        decl.getVariable(0).setInitializer(loadCall);
                    } else if (contextNode instanceof AssignExpr) {
                        AssignExpr assign = (AssignExpr) contextNode;
                        assign.setValue(loadCall);
                    }
                    
                    // Remove the setter calls
                    setterCalls.forEach(call -> {
                        Optional<Statement> parentStmt = call.findAncestor(Statement.class);
                        parentStmt.ifPresent(stmt -> {
                            Optional<Node> parent = stmt.getParentNode();
                            if (parent.isPresent() && parent.get() instanceof BlockStmt) {
                                BlockStmt parentBlock = (BlockStmt) parent.get();
                                parentBlock.getStatements().remove(stmt);
                            }
                        });
                    });
                }
            }
        }
        
        private List<MethodCallExpr> findSetterCalls(BlockStmt block, String variableName, Node afterNode) {
            List<MethodCallExpr> setters = new ArrayList<>();
            boolean foundContext = false;
            
            for (Statement stmt : block.getStatements()) {
                if (!foundContext) {
                    // Check if we've found the context node (variable declaration or assignment)
                    if (stmt.equals(afterNode) || containsNode(stmt, afterNode)) {
                        foundContext = true;
                    }
                    continue;
                }
                
                // Look for method calls on the flyway variable
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) expr;
                        if (isSetterCallOnVariable(methodCall, variableName)) {
                            setters.add(methodCall);
                        } else {
                            // Stop at first non-setter call
                            break;
                        }
                    } else {
                        // Stop at first non-method-call expression
                        break;
                    }
                } else {
                    // Stop at first non-expression statement
                    break;
                }
            }
            
            return setters;
        }
        
        private boolean containsNode(Node parent, Node child) {
            return parent.getChildNodes().stream()
                .anyMatch(node -> node == child || containsNode(node, child));
        }
        
        private boolean isSetterCallOnVariable(MethodCallExpr call, String variableName) {
            if (!call.getScope().isPresent()) {
                return false;
            }
            
            Expression scope = call.getScope().get();
            if (scope instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) scope;
                return nameExpr.getNameAsString().equals(variableName) &&
                       METHOD_MAPPING.containsKey(call.getNameAsString());
            }
            
            return false;
        }
    }
}