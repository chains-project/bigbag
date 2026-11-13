package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        try {
            transformFlywayApi(new File(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformFlywayApi(File sourceDir) throws IOException {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        try (Stream<Path> paths = Files.walk(sourceDir.toPath())) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(p -> transformFile(p));
        }
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + filePath)
            );
            
            FlywayApiTransformer transformer = new FlywayApiTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                    System.out.println("Modified: " + filePath);
                }
            }
        } catch (Exception e) {
            System.err.println("Error transforming " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class FlywayApiTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Process each method to find Flyway creation patterns
            BlockStmt body = n.getBody().orElse(null);
            if (body == null) {
                return super.visit(n, arg);
            }
            
            // Find all Flyway variable declarations in this method
            List<FlywayCreationInfo> flywayCreations = findFlywayCreations(body);
            
            for (FlywayCreationInfo creation : flywayCreations) {
                if (transformFlywayCreation(creation, body)) {
                    modified = true;
                }
            }
            
            return super.visit(n, arg);
        }
        
        private List<FlywayCreationInfo> findFlywayCreations(BlockStmt body) {
            List<FlywayCreationInfo> creations = new ArrayList<>();
            
            for (Statement stmt : body.getStatements()) {
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                        varDecl.getVariables().forEach(v -> {
                            if (v.getInitializer().isPresent()) {
                                Expression init = v.getInitializer().get();
                                if (init instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creation = (ObjectCreationExpr) init;
                                    String typeName = creation.getType().asString();
                                    if (isFlywayType(typeName)) {
                                        creations.add(new FlywayCreationInfo(v.getNameAsString(), creation, stmt));
                                    }
                                }
                            }
                        });
                    }
                }
            }
            
            return creations;
        }
        
        private boolean isFlywayType(String typeName) {
            return typeName.equals("Flyway") || 
                   typeName.equals("org.flywaydb.core.Flyway") ||
                   typeName.endsWith(".Flyway"); // Handle imports
        }
        
        private boolean transformFlywayCreation(FlywayCreationInfo creation, BlockStmt body) {
            String varName = creation.varName;
            ObjectCreationExpr oldConstructor = creation.constructor;
            Statement creationStmt = creation.creationStatement;
            
            // Check if it's a no-args constructor (old API)
            if (!oldConstructor.getArguments().isEmpty()) {
                return false;
            }
            
            // Find all setter calls on this variable after its declaration
            List<SetterCallInfo> setterCalls = findSetterCalls(varName, body, creationStmt);
            
            if (setterCalls.isEmpty()) {
                return false;
            }
            
            // Get the type name to use (could be simple name or fully qualified)
            String typeName = oldConstructor.getType().asString();
            
            // Build new fluent API call
            MethodCallExpr configureCall;
            
            // Check if we need to pass ClassLoader to configure()
            Optional<SetterCallInfo> classLoaderSetter = setterCalls.stream()
                .filter(s -> s.methodName.equals("setClassLoader"))
                .findFirst();
            
            if (classLoaderSetter.isPresent()) {
                // Use configure(ClassLoader) overload
                configureCall = new MethodCallExpr(
                    new NameExpr(getSimpleName(typeName)), 
                    "configure", 
                    new NodeList<>(classLoaderSetter.get().arguments));
                // Remove this setter from the list since it's handled
                setterCalls.remove(classLoaderSetter.get());
            } else {
                configureCall = new MethodCallExpr(
                    new NameExpr(getSimpleName(typeName)), "configure");
            }
            
            // Build fluent method chain
            MethodCallExpr currentCall = configureCall;
            
            // Map old setter names to new fluent method names
            // General rule: setXyz -> xyz (camelCase)
            for (SetterCallInfo setter : setterCalls) {
                if (setter.methodName.startsWith("set")) {
                    // Convert setXyz to xyz (camelCase)
                    String baseName = setter.methodName.substring(3);
                    if (!baseName.isEmpty()) {
                        String newMethodName = Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
                        currentCall = new MethodCallExpr(currentCall, newMethodName, setter.arguments);
                    }
                }
            }
            
            // Add .load() at the end
            currentCall = new MethodCallExpr(currentCall, "load");
            
            // Replace the variable declaration
            if (creationStmt instanceof ExpressionStmt) {
                ExpressionStmt exprStmt = (ExpressionStmt) creationStmt;
                if (exprStmt.getExpression() instanceof VariableDeclarationExpr) {
                    VariableDeclarationExpr varDecl = (VariableDeclarationExpr) exprStmt.getExpression();
                    // Update the initializer
                    varDecl.getVariables().get(0).setInitializer(currentCall);
                    
                    // Remove all the setter call statements
                    for (SetterCallInfo setter : setterCalls) {
                        body.getStatements().remove(setter.statement);
                    }
                    
                    // Also remove the classLoaderSetter if it was in the list
                    if (classLoaderSetter.isPresent()) {
                        body.getStatements().remove(classLoaderSetter.get().statement);
                    }
                    
                    return true;
                }
            }
            
            return false;
        }
        
        private String getSimpleName(String typeName) {
            if (typeName.contains(".")) {
                return typeName.substring(typeName.lastIndexOf('.') + 1);
            }
            return typeName;
        }
        
        private List<SetterCallInfo> findSetterCalls(String varName, BlockStmt body, Statement afterStmt) {
            List<SetterCallInfo> setters = new ArrayList<>();
            boolean foundCreation = false;
            
            for (Statement stmt : body.getStatements()) {
                if (stmt == afterStmt) {
                    foundCreation = true;
                    continue;
                }
                
                if (!foundCreation) {
                    continue;
                }
                
                // Check if this statement is a return statement with the variable
                if (stmt instanceof ReturnStmt) {
                    ReturnStmt returnStmt = (ReturnStmt) stmt;
                    if (returnStmt.getExpression().isPresent()) {
                        Expression returnExpr = returnStmt.getExpression().get();
                        if (returnExpr instanceof NameExpr) {
                            String returnVarName = ((NameExpr) returnExpr).getNameAsString();
                            if (returnVarName.equals(varName)) {
                                // We've reached the return statement, stop looking
                                break;
                            }
                        }
                    }
                }
                
                // Check if this is a setter call on our variable
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) expr;
                        if (methodCall.getScope().isPresent()) {
                            Expression scope = methodCall.getScope().get();
                            if (scope instanceof NameExpr) {
                                String scopeName = ((NameExpr) scope).getNameAsString();
                                if (scopeName.equals(varName)) {
                                    String methodName = methodCall.getNameAsString();
                                    if (methodName.startsWith("set")) {
                                        setters.add(new SetterCallInfo(methodName, methodCall.getArguments(), stmt));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return setters;
        }
        
        private static class FlywayCreationInfo {
            String varName;
            ObjectCreationExpr constructor;
            Statement creationStatement;
            
            FlywayCreationInfo(String varName, ObjectCreationExpr constructor, Statement creationStatement) {
                this.varName = varName;
                this.constructor = constructor;
                this.creationStatement = creationStatement;
            }
        }
        
        private static class SetterCallInfo {
            String methodName;
            NodeList<Expression> arguments;
            Statement statement;
            
            SetterCallInfo(String methodName, NodeList<Expression> arguments, Statement statement) {
                this.methodName = methodName;
                this.arguments = arguments;
                this.statement = statement;
            }
        }
    }
}