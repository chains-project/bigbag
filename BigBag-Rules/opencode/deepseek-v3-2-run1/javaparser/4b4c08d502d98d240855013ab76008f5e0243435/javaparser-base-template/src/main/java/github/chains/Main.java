package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Flyway API usage from v8.x to v9.x pattern");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Scanning for Java files in: " + sourceDir);
        
        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        // Setup JavaParser with type resolution
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        int modifiedFiles = 0;
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                FlywayApiTransformer transformer = new FlywayApiTransformer();
                cu.accept(transformer, null);
                
                if (transformer.getTransformationsApplied() > 0) {
                    String newContent = cu.toString();
                    Files.write(javaFile, newContent.getBytes());
                    System.out.println("Modified: " + javaFile + " (" + transformer.getTransformationsApplied() + " transformations)");
                    modifiedFiles++;
                    totalTransformations += transformer.getTransformationsApplied();
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("Modified " + modifiedFiles + " files");
        System.out.println("Applied " + totalTransformations + " transformations");
    }
    
    /**
     * Transformer for Flyway API from v8.x to v9.x.
     * 
     * Transforms patterns like:
     *   Flyway flyway = new Flyway();
     *   flyway.setDataSource(dataSource);
     *   flyway.setClassLoader(classLoader);
     *   flyway.setLocations(locations);
     *   flyway.setValidateOnMigrate(validate);
     *   return flyway;
     * 
     * To:
     *   return Flyway.configure()
     *       .dataSource(dataSource)
     *       .classLoader(classLoader)
     *       .locations(locations)
     *       .validateMigration(validate)
     *       .load();
     */
    private static class FlywayApiTransformer extends ModifierVisitor<Void> {
        private int transformationsApplied = 0;
        
        public int getTransformationsApplied() {
            return transformationsApplied;
        }
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Look for methods that create and configure Flyway instances
            BlockStmt body = n.getBody().orElse(null);
            if (body == null) {
                return super.visit(n, arg);
            }
            
            List<Statement> statements = body.getStatements();
            Map<String, FlywayCreationInfo> flywayCreations = new HashMap<>();
            
            // First pass: Find all Flyway variable declarations
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                
                // Look for: Flyway flyway = new Flyway();
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                        for (VariableDeclarator var : varDecl.getVariables()) {
                            String typeName = varDecl.getCommonType().asString();
                            if (isFlywayType(typeName) && var.getInitializer().isPresent()) {
                                Expression init = var.getInitializer().get();
                                if (init instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creation = (ObjectCreationExpr) init;
                                    if (creation.getArguments().isEmpty() && 
                                        (creation.getType().asString().equals("Flyway") || 
                                         creation.getType().asString().equals("org.flywaydb.core.Flyway"))) {
                                        
                                        String varName = var.getNameAsString();
                                        flywayCreations.put(varName, new FlywayCreationInfo(varName, i));
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Look for: flyway = new Flyway();
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof AssignExpr) {
                        AssignExpr assign = (AssignExpr) expr;
                        if (assign.getTarget() instanceof NameExpr) {
                            String varName = ((NameExpr) assign.getTarget()).getNameAsString();
                            if (assign.getValue() instanceof ObjectCreationExpr) {
                                ObjectCreationExpr creation = (ObjectCreationExpr) assign.getValue();
                                if (creation.getArguments().isEmpty() && 
                                    (creation.getType().asString().equals("Flyway") || 
                                     creation.getType().asString().equals("org.flywaydb.core.Flyway"))) {
                                    
                                    flywayCreations.put(varName, new FlywayCreationInfo(varName, i));
                                }
                            }
                        }
                    }
                }
            }
            
            // Second pass: Collect setter calls for each Flyway variable
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    
                    // Look for flyway.setXxx(...) calls
                    if (expr instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) expr;
                        Optional<Expression> scope = methodCall.getScope();
                        
                        if (scope.isPresent() && scope.get() instanceof NameExpr) {
                            String varName = ((NameExpr) scope.get()).getNameAsString();
                            FlywayCreationInfo info = flywayCreations.get(varName);
                            
                            if (info != null && i > info.creationIndex) {
                                String methodName = methodCall.getNameAsString();
                                
                                // Map old setter names to new fluent API method names
                                if (methodName.equals("setDataSource")) {
                                    info.dataSourceArg = methodCall.getArgument(0);
                                    info.setterIndexes.add(i);
                                } else if (methodName.equals("setClassLoader")) {
                                    info.classLoaderArg = methodCall.getArgument(0);
                                    info.setterIndexes.add(i);
                                } else if (methodName.equals("setLocations")) {
                                    info.locationsArg = methodCall.getArgument(0);
                                    info.setterIndexes.add(i);
                                } else if (methodName.equals("setValidateOnMigrate")) {
                                    info.validateArg = methodCall.getArgument(0);
                                    info.setterIndexes.add(i);
                                }
                            }
                        }
                    }
                    
                    // Look for return flyway;
                    if (expr instanceof NameExpr) {
                        String varName = ((NameExpr) expr).getNameAsString();
                        FlywayCreationInfo info = flywayCreations.get(varName);
                        
                        if (info != null && i > info.creationIndex) {
                            // Check if this is actually a return statement
                            // We need to look at the parent
                            Node parent = stmt.getParentNode().orElse(null);
                            if (parent instanceof ReturnStmt) {
                                info.returnIndex = i;
                                info.returnVarName = varName;
                            }
                        }
                    }
                }
                
                // Look for return flyway; statements
                if (stmt instanceof ReturnStmt) {
                    ReturnStmt returnStmt = (ReturnStmt) stmt;
                    Optional<Expression> returnExpr = returnStmt.getExpression();
                    
                    if (returnExpr.isPresent() && returnExpr.get() instanceof NameExpr) {
                        String varName = ((NameExpr) returnExpr.get()).getNameAsString();
                        FlywayCreationInfo info = flywayCreations.get(varName);
                        
                        if (info != null) {
                            info.returnIndex = i;
                            info.returnVarName = varName;
                        }
                    }
                }
            }
            
            // Third pass: Apply transformations for complete Flyway creation patterns
            for (FlywayCreationInfo info : flywayCreations.values()) {
                // Only transform if we have a return statement and at least one setter
                if (info.returnIndex > 0 && !info.setterIndexes.isEmpty()) {
                    // Build the new fluent API expression
                    // Check if we have a classLoader to pass to configure()
                    MethodCallExpr configureCall;
                    if (info.classLoaderArg != null) {
                        configureCall = new MethodCallExpr(
                            new NameExpr("Flyway"), "configure", 
                            new NodeList<>(info.classLoaderArg.clone()));
                    } else {
                        configureCall = new MethodCallExpr(
                            new NameExpr("Flyway"), "configure");
                    }
                    
                    MethodCallExpr currentCall = configureCall;
                    
                    // Add configuration methods (skip classLoader since it was passed to configure())
                    if (info.dataSourceArg != null) {
                        currentCall = new MethodCallExpr(currentCall, "dataSource", new NodeList<>(info.dataSourceArg.clone()));
                    }
                    if (info.locationsArg != null) {
                        // locations() may need array conversion
                        currentCall = new MethodCallExpr(currentCall, "locations", new NodeList<>(info.locationsArg.clone()));
                    }
                    if (info.validateArg != null) {
                        currentCall = new MethodCallExpr(currentCall, "validateOnMigrate", new NodeList<>(info.validateArg.clone()));
                    }
                    
                    // Add .load() to create the Flyway instance
                    MethodCallExpr loadCall = new MethodCallExpr(currentCall, "load");
                    
                    // Replace the return statement with the new expression
                    if (info.returnIndex < statements.size()) {
                        Statement returnStmt = statements.get(info.returnIndex);
                        if (returnStmt instanceof ReturnStmt) {
                            ((ReturnStmt) returnStmt).setExpression(loadCall);
                            
                            // Mark all statements to be removed (creation and setters)
                            info.setterIndexes.add(info.creationIndex);
                            
                            // Remove the old statements in reverse order
                            List<Integer> indexesToRemove = new ArrayList<>(info.setterIndexes);
                            Collections.sort(indexesToRemove, Collections.reverseOrder());
                            
                            for (int index : indexesToRemove) {
                                if (index != info.returnIndex) { // Don't remove the return statement
                                    body.getStatements().remove(index);
                                }
                            }
                            
                            transformationsApplied++;
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isFlywayType(String typeName) {
            return typeName.equals("Flyway") || 
                   typeName.equals("org.flywaydb.core.Flyway") ||
                   typeName.endsWith(".Flyway");
        }
        
        private class FlywayCreationInfo {
            String varName;
            int creationIndex;
            int returnIndex = -1;
            String returnVarName;
            List<Integer> setterIndexes = new ArrayList<>();
            Expression dataSourceArg;
            Expression classLoaderArg;
            Expression locationsArg;
            Expression validateArg;
            
            FlywayCreationInfo(String varName, int creationIndex) {
                this.varName = varName;
                this.creationIndex = creationIndex;
            }
        }
    }
}