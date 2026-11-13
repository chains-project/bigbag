package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.printer.*;
import com.github.javaparser.printer.configuration.*;
import com.github.javaparser.resolution.*;
import com.github.javaparser.symbolsolver.*;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Main {
    // Mapping from old setter methods to new fluent API methods
    private static final Map<String, String> METHOD_MAPPING = new HashMap<>();
    static {
        METHOD_MAPPING.put("setDataSource", "dataSource");
        METHOD_MAPPING.put("setLocations", "locations");
        METHOD_MAPPING.put("setValidateOnMigrate", "validateOnMigrate");
        METHOD_MAPPING.put("setBaselineOnMigrate", "baselineOnMigrate");
        METHOD_MAPPING.put("setBaselineVersion", "baselineVersion");
        METHOD_MAPPING.put("setPlaceholders", "placeholders");
        METHOD_MAPPING.put("setEncoding", "encoding");
        METHOD_MAPPING.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        METHOD_MAPPING.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        METHOD_MAPPING.put("setTable", "table");
        METHOD_MAPPING.put("setTarget", "target");
        METHOD_MAPPING.put("setOutOfOrder", "outOfOrder");
        METHOD_MAPPING.put("setCleanOnValidationError", "cleanOnValidationError");
        METHOD_MAPPING.put("setCleanDisabled", "cleanDisabled");
        METHOD_MAPPING.put("setMixed", "mixed");
        METHOD_MAPPING.put("setGroup", "group");
        METHOD_MAPPING.put("setInstalledBy", "installedBy");
        METHOD_MAPPING.put("setSchemas", "schemas");
        METHOD_MAPPING.put("setDefaultSchema", "defaultSchema");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <classpath> github.chains.Main <source-directory>");
            System.err.println("Transforms Flyway API from old setter pattern to new fluent configuration pattern.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        try {
            int transformedFiles = transformFlywayApi(sourceDir);
            System.out.println("Transformation completed. Modified " + transformedFiles + " file(s).");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformFlywayApi(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedCount = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                if (transformFile(javaFile)) {
                    transformedCount++;
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        return transformedCount;
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        // Quick check if file contains Flyway references
        if (!content.contains("Flyway") && !content.contains("flyway")) {
            return false;
        }
        
        // Parse the Java file
        StaticJavaParser.getConfiguration().setSymbolResolver(new JavaSymbolSolver(
            new ReflectionTypeSolver(false)
        ));
        
        CompilationUnit cu = StaticJavaParser.parse(content);
        boolean changed = false;
        
        // Strategy: Find all ObjectCreationExpr for Flyway and transform them
        List<ObjectCreationExpr> flywayCreations = cu.findAll(ObjectCreationExpr.class).stream()
            .filter(expr -> {
                String typeName = expr.getType().asString();
                return typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway");
            })
            .collect(Collectors.toList());
        
        for (ObjectCreationExpr creation : flywayCreations) {
            // Only transform no-arg constructor calls
            if (creation.getArguments().isEmpty()) {
                // Get the parent node to understand the context
                Node parent = creation.getParentNode().orElse(null);
                
                if (parent instanceof VariableDeclarator) {
                    // Case: Flyway flyway = new Flyway();
                    changed = transformVariableDeclaration((VariableDeclarator) parent, creation, cu) || changed;
                } else if (parent instanceof MethodCallExpr) {
                    // Case: new Flyway().setDataSource(...)
                    changed = transformInlineChain((MethodCallExpr) parent, creation, cu) || changed;
                } else if (parent instanceof ExpressionStmt) {
                    // Case: (less common) flyway = new Flyway();
                    // We'll handle this by looking for assignments
                    changed = transformAssignment((ExpressionStmt) parent, creation, cu) || changed;
                }
            }
        }
        
        if (changed) {
            // Pretty print the transformed code
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String transformed = printer.print(cu);
            Files.writeString(filePath, transformed);
            System.out.println("Transformed: " + filePath);
            return true;
        }
        
        return false;
    }
    
    private static boolean transformVariableDeclaration(VariableDeclarator varDecl, 
                                                       ObjectCreationExpr creation,
                                                       CompilationUnit cu) {
        String varName = varDecl.getNameAsString();
        
        // Find the block containing this variable declaration
        Optional<BlockStmt> blockOpt = varDecl.findAncestor(BlockStmt.class);
        if (!blockOpt.isPresent()) {
            return false;
        }
        
        BlockStmt block = blockOpt.get();
        List<Statement> statements = block.getStatements();
        
        // Find the statement index of our variable declaration
        int varIndex = -1;
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).toString().contains("Flyway " + varName)) {
                varIndex = i;
                break;
            }
        }
        
        if (varIndex == -1) {
            return false;
        }
        
        // Collect setter calls that follow the declaration
        List<MethodCallExpr> setterCalls = new ArrayList<>();
        List<Integer> setterIndices = new ArrayList<>();
        Expression classLoaderArg = null;
        
        for (int i = varIndex + 1; i < statements.size(); i++) {
            Statement stmt = statements.get(i);
            if (stmt instanceof ExpressionStmt) {
                Expression expr = ((ExpressionStmt) stmt).getExpression();
                if (expr instanceof MethodCallExpr) {
                    MethodCallExpr mce = (MethodCallExpr) expr;
                    
                    // Check if this is a method call on our flyway variable
                    if (mce.getScope().isPresent()) {
                        Expression scope = mce.getScope().get();
                        if (scope.toString().equals(varName)) {
                            String methodName = mce.getNameAsString();
                            if (methodName.equals("setClassLoader")) {
                                // Special handling for setClassLoader
                                if (!mce.getArguments().isEmpty()) {
                                    classLoaderArg = mce.getArguments().get(0);
                                }
                                setterIndices.add(i);
                            } else if (METHOD_MAPPING.containsKey(methodName)) {
                                setterCalls.add(mce);
                                setterIndices.add(i);
                            } else {
                                // Not a setter we recognize, break the chain
                                break;
                            }
                        } else {
                            // Not a call on our variable, break
                            break;
                        }
                    } else {
                        // No scope, not our method call
                        break;
                    }
                } else {
                    // Not a method call, break
                    break;
                }
            } else {
                // Not an expression statement, break
                break;
            }
        }
        
        if (setterCalls.isEmpty() && classLoaderArg == null) {
            return false;
        }
        
        // Build the new fluent configuration chain
        MethodCallExpr configureCall;
        if (classLoaderArg != null) {
            // Pass ClassLoader to configure() method
            configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), "configure", new NodeList<>(classLoaderArg.clone()));
        } else {
            configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), "configure");
        }
        
        MethodCallExpr currentChain = configureCall;
        
        // Add each setter as a fluent method call
        for (MethodCallExpr setter : setterCalls) {
            String oldMethod = setter.getNameAsString();
            String newMethod = METHOD_MAPPING.get(oldMethod);
            
            MethodCallExpr fluentCall = new MethodCallExpr(currentChain, newMethod);
            
            // Copy arguments
            for (Expression arg : setter.getArguments()) {
                fluentCall.addArgument(arg.clone());
            }
            
            currentChain = fluentCall;
        }
        
        // Add .load() at the end
        MethodCallExpr loadCall = new MethodCallExpr(currentChain, "load");
        
        // Update the initializer
        varDecl.setInitializer(loadCall);
        
        // Remove the setter statements (from highest index to lowest)
        Collections.reverse(setterIndices);
        for (int index : setterIndices) {
            block.getStatements().remove(index);
        }
        
        return true;
    }
    
    private static boolean transformInlineChain(MethodCallExpr chain,
                                               ObjectCreationExpr creation,
                                               CompilationUnit cu) {
        // Extract the method call chain starting from the creation
        List<MethodCallExpr> chainCalls = new ArrayList<>();
        MethodCallExpr current = chain;
        Expression classLoaderArg = null;
        
        while (true) {
            chainCalls.add(current);
            
            // Check if the scope is another method call
            if (current.getScope().isPresent() && 
                current.getScope().get() instanceof MethodCallExpr) {
                current = (MethodCallExpr) current.getScope().get();
            } else if (current.getScope().isPresent() && 
                       current.getScope().get() instanceof ObjectCreationExpr) {
                // Found the creation, stop
                break;
            } else {
                // Unexpected structure
                return false;
            }
        }
        
        // Reverse to get correct order (from first to last)
        Collections.reverse(chainCalls);
        
        // Check for setClassLoader in the chain
        for (MethodCallExpr mce : chainCalls) {
            if (mce.getNameAsString().equals("setClassLoader") && !mce.getArguments().isEmpty()) {
                classLoaderArg = mce.getArguments().get(0);
                // Remove this from the chain as we'll handle it differently
                chainCalls.remove(mce);
                break;
            }
        }
        
        if (chainCalls.isEmpty() && classLoaderArg == null) {
            return false;
        }
        
        // Check if first method is a setter (if there are any)
        if (!chainCalls.isEmpty() && !METHOD_MAPPING.containsKey(chainCalls.get(0).getNameAsString())) {
            return false;
        }
        
        // Build new chain starting with Flyway.configure()
        MethodCallExpr configureCall;
        if (classLoaderArg != null) {
            configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), "configure", new NodeList<>(classLoaderArg.clone()));
        } else {
            configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), "configure");
        }
        
        MethodCallExpr currentChain = configureCall;
        
        for (MethodCallExpr mce : chainCalls) {
            String oldMethod = mce.getNameAsString();
            if (METHOD_MAPPING.containsKey(oldMethod)) {
                String newMethod = METHOD_MAPPING.get(oldMethod);
                MethodCallExpr fluentCall = new MethodCallExpr(currentChain, newMethod);
                
                for (Expression arg : mce.getArguments()) {
                    fluentCall.addArgument(arg.clone());
                }
                
                currentChain = fluentCall;
            } else {
                // Not a mapped method, can't transform
                return false;
            }
        }
        
        // Add .load() at the end
        MethodCallExpr loadCall = new MethodCallExpr(currentChain, "load");
        
        // Replace the entire chain
        chain.replace(loadCall);
        
        return true;
    }
    
    private static boolean transformAssignment(ExpressionStmt stmt,
                                              ObjectCreationExpr creation,
                                              CompilationUnit cu) {
        // This handles cases like: flyway = new Flyway();
        // We need to find the variable name and then look for setters
        
        Expression expr = stmt.getExpression();
        if (!(expr instanceof AssignExpr)) {
            return false;
        }
        
        AssignExpr assign = (AssignExpr) expr;
        Expression target = assign.getTarget();
        
        if (!(target instanceof NameExpr)) {
            return false;
        }
        
        String varName = ((NameExpr) target).getNameAsString();
        
        // Now we need to find this statement and subsequent setters
        // Similar to transformVariableDeclaration but for assignment statements
        
        Optional<BlockStmt> blockOpt = stmt.findAncestor(BlockStmt.class);
        if (!blockOpt.isPresent()) {
            return false;
        }
        
        BlockStmt block = blockOpt.get();
        List<Statement> statements = block.getStatements();
        
        // Find the statement index
        int stmtIndex = -1;
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).equals(stmt)) {
                stmtIndex = i;
                break;
            }
        }
        
        if (stmtIndex == -1) {
            return false;
        }
        
        // Collect setter calls that follow
        List<MethodCallExpr> setterCalls = new ArrayList<>();
        List<Integer> setterIndices = new ArrayList<>();
        Expression classLoaderArg = null;
        
        for (int i = stmtIndex + 1; i < statements.size(); i++) {
            Statement s = statements.get(i);
            if (s instanceof ExpressionStmt) {
                Expression e = ((ExpressionStmt) s).getExpression();
                if (e instanceof MethodCallExpr) {
                    MethodCallExpr mce = (MethodCallExpr) e;
                    
                    if (mce.getScope().isPresent()) {
                        Expression scope = mce.getScope().get();
                        if (scope.toString().equals(varName)) {
                            String methodName = mce.getNameAsString();
                            if (methodName.equals("setClassLoader")) {
                                if (!mce.getArguments().isEmpty()) {
                                    classLoaderArg = mce.getArguments().get(0);
                                }
                                setterIndices.add(i);
                            } else if (METHOD_MAPPING.containsKey(methodName)) {
                                setterCalls.add(mce);
                                setterIndices.add(i);
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
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
        
        if (setterCalls.isEmpty() && classLoaderArg == null) {
            return false;
        }
        
        // Build new chain
        MethodCallExpr configureCall;
        if (classLoaderArg != null) {
            configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), "configure", new NodeList<>(classLoaderArg.clone()));
        } else {
            configureCall = new MethodCallExpr(
                new NameExpr("Flyway"), "configure");
        }
        
        MethodCallExpr currentChain = configureCall;
        
        for (MethodCallExpr setter : setterCalls) {
            String oldMethod = setter.getNameAsString();
            String newMethod = METHOD_MAPPING.get(oldMethod);
            
            MethodCallExpr fluentCall = new MethodCallExpr(currentChain, newMethod);
            
            for (Expression arg : setter.getArguments()) {
                fluentCall.addArgument(arg.clone());
            }
            
            currentChain = fluentCall;
        }
        
        // Add .load() at the end
        MethodCallExpr loadCall = new MethodCallExpr(currentChain, "load");
        
        // Update the assignment
        assign.setValue(loadCall);
        
        // Remove the setter statements
        Collections.reverse(setterIndices);
        for (int index : setterIndices) {
            block.getStatements().remove(index);
        }
        
        return true;
    }
}