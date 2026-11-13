package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        transformFlywayAPI(sourceDir);
        System.out.println("Transformation complete.");
    }
    
    private static void transformFlywayAPI(Path sourceDir) throws IOException {
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(file -> transformFile(file));
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            if (cu == null) {
                return;
            }
            
            FlywayTransformer transformer = new FlywayTransformer();
            Visitable transformed = transformer.visit(cu, null);
            
            if (transformed instanceof CompilationUnit) {
                cu = (CompilationUnit) transformed;
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Transformed: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private static final Map<String, String> SETTER_TO_FLUENT_METHOD = new HashMap<>();
        static {
            SETTER_TO_FLUENT_METHOD.put("setDataSource", "dataSource");
            SETTER_TO_FLUENT_METHOD.put("setLocations", "locations");
            SETTER_TO_FLUENT_METHOD.put("setValidateOnMigrate", "validateOnMigrate");
        }
        
        private final Map<String, List<MethodCallExpr>> flywayDeclarations = new HashMap<>();
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            String typeName = n.getType().asString();
            if (("Flyway".equals(typeName) || "org.flywaydb.core.Flyway".equals(typeName)) && n.getArguments().isEmpty()) {
                String qualifiedType = typeName.contains(".") ? "org.flywaydb.core.Flyway" : "Flyway";
                return new MethodCallExpr(new NameExpr(qualifiedType), "configure");
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            n = (VariableDeclarationExpr) super.visit(n, arg);
            for (var variable : n.getVariables()) {
                variable.getInitializer().ifPresent(init -> {
                    if (init instanceof MethodCallExpr) {
                        MethodCallExpr call = (MethodCallExpr) init;
                        if (call.getNameAsString().equals("configure")) {
                            flywayDeclarations.put(variable.getNameAsString(), new ArrayList<>());
                        }
                    }
                });
            }
            return n;
        }
        
        @Override
        public Visitable visit(AssignExpr n, Void arg) {
            n = (AssignExpr) super.visit(n, arg);
            if (n.getTarget() instanceof NameExpr && n.getValue() instanceof MethodCallExpr) {
                NameExpr target = (NameExpr) n.getTarget();
                MethodCallExpr call = (MethodCallExpr) n.getValue();
                if (call.getNameAsString().equals("configure")) {
                    flywayDeclarations.put(target.getNameAsString(), new ArrayList<>());
                }
            }
            return n;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            n = (MethodCallExpr) super.visit(n, arg);
            if (n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String varName = ((NameExpr) scope).getNameAsString();
                    String methodName = n.getNameAsString();
                    if (flywayDeclarations.containsKey(varName) && methodName.startsWith("set")) {
                        flywayDeclarations.get(varName).add(n.clone());
                        if (n.getParentNode().isPresent() && n.getParentNode().get() instanceof ExpressionStmt) {
                            return null;
                        }
                    }
                }
            }
            return n;
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            if (n.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr call = (MethodCallExpr) n.getExpression();
                if (call == null) {
                    return null;
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(BlockStmt n, Void arg) {
            n = (BlockStmt) super.visit(n, arg);
            NodeList<com.github.javaparser.ast.stmt.Statement> newStatements = new NodeList<>();
            for (var stmt : n.getStatements()) {
                if (stmt != null) {
                    newStatements.add(stmt);
                }
            }
            n.setStatements(newStatements);
            return n;
        }
        
        @Override
        public Visitable visit(CompilationUnit n, Void arg) {
            n = (CompilationUnit) super.visit(n, arg);
            n.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr n, Void arg2) {
                    if (n.getNameAsString().equals("configure")) {
                        Optional<Node> parent = n.getParentNode();
                        String varName = findVariableNameForConfigureCall(n, parent);
                        if (varName != null && flywayDeclarations.containsKey(varName)) {
                            MethodCallExpr currentCall = n;
                            List<MethodCallExpr> setters = flywayDeclarations.get(varName);
                            
                            Expression classLoaderArg = null;
                            List<MethodCallExpr> otherSetters = new ArrayList<>();
                            
                            for (MethodCallExpr setter : setters) {
                                if ("setClassLoader".equals(setter.getNameAsString())) {
                                    classLoaderArg = setter.getArguments().get(0).clone();
                                } else {
                                    otherSetters.add(setter);
                                }
                            }
                            
                            if (classLoaderArg != null) {
                                NodeList<Expression> configureArgs = new NodeList<>();
                                configureArgs.add(classLoaderArg);
                                n.setArguments(configureArgs);
                            }
                            
                            for (MethodCallExpr setter : otherSetters) {
                                String oldMethod = setter.getNameAsString();
                                String newMethod = SETTER_TO_FLUENT_METHOD.get(oldMethod);
                                if (newMethod != null) {
                                    NodeList<Expression> args = new NodeList<>();
                                    for (var setterArg : setter.getArguments()) {
                                        args.add(setterArg.clone());
                                    }
                                    MethodCallExpr fluentCall = new MethodCallExpr(currentCall, newMethod);
                                    fluentCall.setArguments(args);
                                    currentCall = fluentCall;
                                }
                            }
                            
                            return new MethodCallExpr(currentCall, "load");
                        }
                    }
                    return super.visit(n, arg2);
                }
                
                private String findVariableNameForConfigureCall(MethodCallExpr configureCall, Optional<Node> parent) {
                    while (parent.isPresent()) {
                        Node p = parent.get();
                        if (p instanceof VariableDeclarationExpr) {
                            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) p;
                            for (var variable : varDecl.getVariables()) {
                                if (variable.getInitializer().isPresent() && variable.getInitializer().get().equals(configureCall)) {
                                    return variable.getNameAsString();
                                }
                            }
                        } else if (p instanceof AssignExpr) {
                            AssignExpr assign = (AssignExpr) p;
                            if (assign.getValue().equals(configureCall) && assign.getTarget() instanceof NameExpr) {
                                return ((NameExpr) assign.getTarget()).getNameAsString();
                            }
                        }
                        parent = p.getParentNode();
                    }
                    return null;
                }
            }, null);
            return n;
        }
    }
}