package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <outputDir>");
            System.err.println("Example: java Main /path/to/src /path/to/transformed");
            System.exit(1);
        }

        String sourceDir = args[0];
        String outputDir = args[1];

        try {
            List<Path> javaFiles = findAllJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");

            for (Path javaFile : javaFiles) {
                processFile(javaFile, sourceDir, outputDir);
            }

            System.out.println("Transformation complete. Output written to: " + outputDir);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<Path> findAllJavaFiles(String sourceDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }

    private static void processFile(Path javaFile, String sourceDir, String outputDir) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();

        FlywayMigrationVisitor visitor = new FlywayMigrationVisitor();
        cu.accept(visitor, null);

        if (visitor.wasModified()) {
            String relativePath = Paths.get(sourceDir).relativize(javaFile).toString();
            Path outputFile = Paths.get(outputDir, relativePath);
            Files.createDirectories(outputFile.getParent());
            
            try (FileWriter writer = new FileWriter(outputFile.toFile())) {
                writer.write(cu.toString());
            }
            
            System.out.println("Modified: " + relativePath);
        }
    }

    static class FlywayMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;

        public boolean wasModified() {
            return modified;
        }

        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            Optional<BlockStmt> body = n.getBody();
            if (body.isPresent()) {
                BlockStmt methodBody = body.get();
                NodeList<Statement> statements = methodBody.getStatements();
                
                // Process each statement looking for Flyway patterns
                for (int i = 0; i < statements.size(); i++) {
                    Statement stmt = statements.get(i);
                    
                    // Check for: Flyway flyway = new Flyway();
                    if (stmt instanceof ExpressionStmt) {
                        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                        if (exprStmt.getExpression() instanceof VariableDeclarationExpr) {
                            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) exprStmt.getExpression();
                            
                            for (VariableDeclarator var : varDecl.getVariables()) {
                                String type = var.getTypeAsString();
                                if (type.equals("Flyway") || type.endsWith(".Flyway")) {
                                    Optional<Expression> initializer = var.getInitializer();
                                    if (initializer.isPresent() && initializer.get() instanceof ObjectCreationExpr) {
                                        ObjectCreationExpr creation = (ObjectCreationExpr) initializer.get();
                                        String creationType = creation.getType().asString();
                                        if (creationType.equals("Flyway") || creationType.endsWith(".Flyway")) {
                                            
                                            if (creation.getArguments().isEmpty()) {
                                                // Found: Flyway flyway = new Flyway();
                                                String varName = var.getNameAsString();
                                                
                                                // Now look for setter calls that follow
                                                List<SetterCall> setters = new ArrayList<>();
                                                boolean needsLoad = false;
                                                
                                                int j = i + 1;
                                                while (j < statements.size()) {
                                                    Statement nextStmt = statements.get(j);
                                                    if (nextStmt instanceof ExpressionStmt) {
                                                        ExpressionStmt nextExprStmt = (ExpressionStmt) nextStmt;
                                                        if (nextExprStmt.getExpression() instanceof MethodCallExpr) {
                                                            MethodCallExpr methodCall = (MethodCallExpr) nextExprStmt.getExpression();
                                                            String methodName = methodCall.getNameAsString();
                                                            
                                                            if (methodName.startsWith("set")) {
                                                                Optional<Expression> scope = methodCall.getScope();
                                                                if (scope.isPresent() && scope.get() instanceof NameExpr) {
                                                                    NameExpr scopeName = (NameExpr) scope.get();
                                                                    if (scopeName.getNameAsString().equals(varName)) {
                                                                        // Found a setter call
                                                                        setters.add(new SetterCall(methodName, methodCall.getArguments()));
                                                                        j++;
                                                                        continue;
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    } else if (nextStmt instanceof ReturnStmt) {
                                                        ReturnStmt returnStmt = (ReturnStmt) nextStmt;
                                                        Optional<Expression> returnExpr = returnStmt.getExpression();
                                                        if (returnExpr.isPresent() && returnExpr.get() instanceof NameExpr) {
                                                            NameExpr returnVar = (NameExpr) returnExpr.get();
                                                            if (returnVar.getNameAsString().equals(varName)) {
                                                                needsLoad = true;
                                                            }
                                                        }
                                                        break;
                                                    }
                                                    break;
                                                }
                                                
                                                // If we found any setters or need to return the Flyway, transform
                                                if (!setters.isEmpty() || needsLoad) {
                                                    // Check if we have a setClassLoader call
                                                    Expression classLoaderArg = null;
                                                    List<SetterCall> otherSetters = new ArrayList<>();
                                                    
                                                    for (SetterCall setter : setters) {
                                                        if (setter.methodName.equals("setClassLoader")) {
                                                            // Save the ClassLoader argument for configure()
                                                            classLoaderArg = setter.arguments.get(0);
                                                        } else {
                                                            otherSetters.add(setter);
                                                        }
                                                    }
                                                    
                                                    // Build the fluent API call chain
                                                    MethodCallExpr configureCall;
                                                    if (classLoaderArg != null) {
                                                        configureCall = new MethodCallExpr(
                                                            new NameExpr("Flyway"), "configure", new NodeList<>(classLoaderArg));
                                                    } else {
                                                        configureCall = new MethodCallExpr(
                                                            new NameExpr("Flyway"), "configure");
                                                    }
                                                    
                                                    Expression currentExpr = configureCall;
                                                    for (SetterCall setter : otherSetters) {
                                                        String fluentMethod = mapSetterToFluentMethod(setter.methodName);
                                                        MethodCallExpr chainedCall = new MethodCallExpr(currentExpr, fluentMethod);
                                                        setter.arguments.forEach(chainedCall::addArgument);
                                                        currentExpr = chainedCall;
                                                    }
                                                    
                                                    if (needsLoad) {
                                                        MethodCallExpr loadCall = new MethodCallExpr(currentExpr, "load");
                                                        var.setInitializer(loadCall);
                                                    } else {
                                                        var.setInitializer(currentExpr);
                                                    }
                                                    
                                                    // Remove the setter statements
                                                    for (int k = 0; k < setters.size(); k++) {
                                                        methodBody.getStatements().remove(i + 1);
                                                    }
                                                    
                                                    modified = true;
                                                }
                                            }
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
        
        private String mapSetterToFluentMethod(String setterName) {
            // Special cases
            if (setterName.equals("setClassLoader")) {
                // ClassLoader is passed to configure() constructor, not a fluent method
                // We'll handle this differently
                return "classLoader";
            }
            if (setterName.equals("setValidateOnMigrate")) {
                return "validateOnMigrate";
            }
            
            // General rule: remove "set" prefix and lowercase first letter
            String baseName = setterName.substring(3);
            String firstChar = baseName.substring(0, 1).toLowerCase();
            return firstChar + baseName.substring(1);
        }
    }
    
    static class SetterCall {
        String methodName;
        NodeList<Expression> arguments;
        
        SetterCall(String methodName, NodeList<Expression> arguments) {
            this.methodName = methodName;
            this.arguments = arguments;
        }
    }
}