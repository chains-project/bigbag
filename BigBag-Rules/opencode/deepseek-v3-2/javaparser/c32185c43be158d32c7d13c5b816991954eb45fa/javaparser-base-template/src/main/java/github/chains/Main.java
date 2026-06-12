package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser-base-template.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .toList();
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int transformedConstructors = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                FlywayTransformer transformer = new FlywayTransformer();
                cu.accept(transformer, null);
                
                if (transformer.isModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    transformedConstructors += transformer.getTransformedCount();
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files modified: " + transformedFiles);
        System.out.println("  Flyway constructors transformed: " + transformedConstructors);
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private int transformedCount = 0;
        
        public boolean isModified() {
            return modified;
        }
        
        public int getTransformedCount() {
            return transformedCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Flyway constructor call
            if (isFlywayConstructor(n)) {
                // Get the variable name if this is assigned to a variable
                Optional<String> varName = getVariableName(n);
                if (varName.isPresent()) {
                    // Look ahead to see if there are setter calls on this object
                    Optional<Node> parent = n.getParentNode();
                    if (parent.isPresent() && parent.get() instanceof ExpressionStmt) {
                        // Check if the next statements are setter calls
                        Optional<Node> grandParent = parent.get().getParentNode();
                        if (grandParent.isPresent() && grandParent.get() instanceof BlockStmt) {
                            BlockStmt block = (BlockStmt) grandParent.get();
                            int index = block.getStatements().indexOf(parent.get());
                            if (index >= 0) {
                                // Collect subsequent setter calls
                                List<Statement> statements = block.getStatements();
                                StringBuilder configBuilder = new StringBuilder();
                                configBuilder.append("Flyway.configure(");
                                
                                // Track if we have a classLoader to pass to configure()
                                Optional<Expression> classLoaderArg = Optional.empty();
                                int setterCount = 0;
                                
                                for (int i = index + 1; i < statements.size(); i++) {
                                    Statement stmt = statements.get(i);
                                    if (stmt instanceof ExpressionStmt) {
                                        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                                        if (exprStmt.getExpression() instanceof MethodCallExpr) {
                                            MethodCallExpr methodCall = (MethodCallExpr) exprStmt.getExpression();
                                            if (methodCall.getScope().isPresent() && 
                                                methodCall.getScope().get().toString().equals(varName.get())) {
                                                String methodName = methodCall.getNameAsString();
                                                NodeList<Expression> arguments = methodCall.getArguments();
                                                
                                                if (methodName.equals("setDataSource") && arguments.size() == 1) {
                                                    // Will add after we close configure()
                                                    setterCount++;
                                                } else if (methodName.equals("setClassLoader") && arguments.size() == 1) {
                                                    classLoaderArg = Optional.of(arguments.get(0));
                                                    setterCount++;
                                                } else if (methodName.equals("setLocations") && arguments.size() == 1) {
                                                    // Will add after we close configure()
                                                    setterCount++;
                                                } else if (methodName.equals("setValidateOnMigrate") && arguments.size() == 1) {
                                                    // Will add after we close configure()
                                                    setterCount++;
                                                } else {
                                                    // Not a setter we recognize, stop processing
                                                    break;
                                                }
                                            } else {
                                                // Not a method call on our variable, stop processing
                                                break;
                                            }
                                        } else {
                                            // Not a method call, stop processing
                                            break;
                                        }
                                    } else {
                                        // Not an expression statement, stop processing
                                        break;
                                    }
                                }
                                
                                // Complete the configure() call
                                if (classLoaderArg.isPresent()) {
                                    configBuilder.append(classLoaderArg.get()).append(")");
                                } else {
                                    configBuilder.append(")");
                                }
                                
                                // Now collect the actual method calls for the builder
                                int collectedSetters = 0;
                                for (int i = index + 1; i < statements.size() && collectedSetters < setterCount; i++) {
                                    Statement stmt = statements.get(i);
                                    if (stmt instanceof ExpressionStmt) {
                                        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                                        if (exprStmt.getExpression() instanceof MethodCallExpr) {
                                            MethodCallExpr methodCall = (MethodCallExpr) exprStmt.getExpression();
                                            if (methodCall.getScope().isPresent() && 
                                                methodCall.getScope().get().toString().equals(varName.get())) {
                                                String methodName = methodCall.getNameAsString();
                                                NodeList<Expression> arguments = methodCall.getArguments();
                                                
                                                if (methodName.equals("setDataSource") && arguments.size() == 1) {
                                                    configBuilder.append(".dataSource(").append(arguments.get(0)).append(")");
                                                    collectedSetters++;
                                                } else if (methodName.equals("setClassLoader") && arguments.size() == 1) {
                                                    // Already handled in configure() call
                                                    collectedSetters++;
                                                } else if (methodName.equals("setLocations") && arguments.size() == 1) {
                                                    configBuilder.append(".locations(").append(arguments.get(0)).append(")");
                                                    collectedSetters++;
                                                } else if (methodName.equals("setValidateOnMigrate") && arguments.size() == 1) {
                                                    configBuilder.append(".validateOnMigrate(").append(arguments.get(0)).append(")");
                                                    collectedSetters++;
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                if (setterCount > 0) {
                                    // Complete the builder chain
                                    configBuilder.append(".load()");
                                    
                                    // Replace the constructor with the builder pattern
                                    NameExpr replacement = StaticJavaParser.parseExpression(configBuilder.toString());
                                    modified = true;
                                    transformedCount++;
                                    
                                    // Remove the setter statements
                                    for (int i = 0; i < setterCount; i++) {
                                        block.getStatements().remove(index + 1);
                                    }
                                    
                                    return replacement;
                                }
                            }
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isFlywayConstructor(ObjectCreationExpr n) {
            String typeName = n.getType().asString();
            // Check for fully qualified or simple name
            return typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway");
        }
        
        private Optional<String> getVariableName(ObjectCreationExpr n) {
            Optional<Node> parent = n.getParentNode();
            if (parent.isPresent()) {
                Node p = parent.get();
                // Check if this is part of a variable declaration
                if (p instanceof com.github.javaparser.ast.body.VariableDeclarator) {
                    com.github.javaparser.ast.body.VariableDeclarator vd = (com.github.javaparser.ast.body.VariableDeclarator) p;
                    return Optional.of(vd.getNameAsString());
                }
            }
            return Optional.empty();
        }
    }
}