package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic transformation for Flyway API migration from version 8.x to 9.x.
 * 
 * Transforms:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(...);
 *   flyway.setClassLoader(...);
 *   flyway.setLocations(...);
 * 
 * To:
 *   Flyway flyway = Flyway.configure(...)  // ClassLoader parameter if setClassLoader was used
 *       .dataSource(...)
 *       .locations(...)
 *       .load();
 * 
 * This transformation is generic and can be applied to any Java project
 * affected by the Flyway 9.x breaking API changes.
 */
public class Main {
    // Map old setter methods to new fluent API methods
    private static final Map<String, String> SETTER_TO_FLUENT = new HashMap<>();
    static {
        SETTER_TO_FLUENT.put("setDataSource", "dataSource");
        SETTER_TO_FLUENT.put("setLocations", "locations");
        SETTER_TO_FLUENT.put("setValidateOnMigrate", "validateOnMigrate");
        SETTER_TO_FLUENT.put("setBaselineDescription", "baselineDescription");
        SETTER_TO_FLUENT.put("setBaselineOnMigrate", "baselineOnMigrate");
        SETTER_TO_FLUENT.put("setBaselineVersion", "baselineVersion");
        SETTER_TO_FLUENT.put("setCallbacks", "callbacks");
        SETTER_TO_FLUENT.put("setCleanDisabled", "cleanDisabled");
        SETTER_TO_FLUENT.put("setCleanOnValidationError", "cleanOnValidationError");
        SETTER_TO_FLUENT.put("setConnectRetries", "connectRetries");
        SETTER_TO_FLUENT.put("setConnectRetriesInterval", "connectRetriesInterval");
        SETTER_TO_FLUENT.put("setCreateSchemas", "createSchemas");
        SETTER_TO_FLUENT.put("setDefaultSchema", "defaultSchema");
        SETTER_TO_FLUENT.put("setEncoding", "encoding");
        SETTER_TO_FLUENT.put("setErrorOverrides", "errorOverrides");
        SETTER_TO_FLUENT.put("setGroup", "group");
        SETTER_TO_FLUENT.put("setIgnoreMigrationPatterns", "ignoreMigrationPatterns");
        SETTER_TO_FLUENT.put("setInitSql", "initSql");
        SETTER_TO_FLUENT.put("setInstalledBy", "installedBy");
        SETTER_TO_FLUENT.put("setJavaMigrations", "javaMigrations");
        SETTER_TO_FLUENT.put("setLockRetryCount", "lockRetryCount");
        SETTER_TO_FLUENT.put("setMixed", "mixed");
        SETTER_TO_FLUENT.put("setOutOfOrder", "outOfOrder");
        SETTER_TO_FLUENT.put("setOutputQueryResults", "outputQueryResults");
        SETTER_TO_FLUENT.put("setPlaceholderPrefix", "placeholderPrefix");
        SETTER_TO_FLUENT.put("setPlaceholderReplacement", "placeholderReplacement");
        SETTER_TO_FLUENT.put("setPlaceholderSeparator", "placeholderSeparator");
        SETTER_TO_FLUENT.put("setPlaceholderSuffix", "placeholderSuffix");
        SETTER_TO_FLUENT.put("setPlaceholders", "placeholders");
        SETTER_TO_FLUENT.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        SETTER_TO_FLUENT.put("setResolvers", "resolvers");
        SETTER_TO_FLUENT.put("setSchemas", "schemas");
        SETTER_TO_FLUENT.put("setScriptPlaceholderPrefix", "scriptPlaceholderPrefix");
        SETTER_TO_FLUENT.put("setScriptPlaceholderSuffix", "scriptPlaceholderSuffix");
        SETTER_TO_FLUENT.put("setSkipDefaultCallbacks", "skipDefaultCallbacks");
        SETTER_TO_FLUENT.put("setSkipDefaultResolvers", "skipDefaultResolvers");
        SETTER_TO_FLUENT.put("setSkipExecutingMigrations", "skipExecutingMigrations");
        SETTER_TO_FLUENT.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        SETTER_TO_FLUENT.put("setSqlMigrationSeparator", "sqlMigrationSeparator");
        SETTER_TO_FLUENT.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        SETTER_TO_FLUENT.put("setTable", "table");
        SETTER_TO_FLUENT.put("setTablespace", "tablespace");
        SETTER_TO_FLUENT.put("setTarget", "target");
        SETTER_TO_FLUENT.put("setUndoSqlMigrationPrefix", "undoSqlMigrationPrefix");
        SETTER_TO_FLUENT.put("setValidateMigrationNaming", "validateMigrationNaming");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java github.chains.Main <source-directory>");
            System.out.println();
            System.out.println("This transformation fixes Flyway API breaking changes from version 8.x to 9.x.");
            System.out.println("It converts old-style Flyway initialization:");
            System.out.println("  Flyway flyway = new Flyway();");
            System.out.println("  flyway.setDataSource(...);");
            System.out.println("  flyway.setClassLoader(...);");
            System.out.println("To new fluent API:");
            System.out.println("  Flyway flyway = Flyway.configure(...)");
            System.out.println("      .dataSource(...)");
            System.out.println("      .load();");
            return;
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            boolean fileModified = processFile(parser, javaFile);
            if (fileModified) {
                totalTransformations++;
                System.out.println("  Modified: " + javaFile);
            }
        }
        
        System.out.println("\nTransformation complete! Modified " + totalTransformations + " files.");
    }
    
    private static boolean processFile(JavaParser parser, Path javaFile) throws Exception {
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        boolean[] modified = {false};
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(BlockStmt block, Void arg) {
                // Process each block to find Flyway initialization patterns
                List<Statement> statements = new ArrayList<>(block.getStatements());
                Map<String, List<MethodCallExpr>> flywaySetters = new HashMap<>();
                Map<String, Integer> flywayDeclIndices = new HashMap<>();
                
                // First pass: identify Flyway declarations and collect their setter calls
                for (int i = 0; i < statements.size(); i++) {
                    Statement stmt = statements.get(i);
                    String varName = extractFlywayVarName(stmt);
                    if (varName != null) {
                        flywayDeclIndices.put(varName, i);
                        flywaySetters.put(varName, new ArrayList<>());
                    } else if (stmt instanceof ExpressionStmt) {
                        Expression expr = ((ExpressionStmt) stmt).getExpression();
                        if (expr instanceof MethodCallExpr) {
                            MethodCallExpr methodCall = (MethodCallExpr) expr;
                            Optional<Expression> scope = methodCall.getScope();
                            if (scope.isPresent()) {
                                String calledVar = scope.get().toString();
                                if (flywaySetters.containsKey(calledVar) && 
                                    methodCall.getNameAsString().startsWith("set")) {
                                    flywaySetters.get(calledVar).add(methodCall);
                                }
                            }
                        }
                    }
                }
                
                // Second pass: transform Flyway initializations that have setters
                for (Map.Entry<String, Integer> entry : flywayDeclIndices.entrySet()) {
                    String varName = entry.getKey();
                    int declIndex = entry.getValue();
                    List<MethodCallExpr> setters = flywaySetters.get(varName);
                    
                    if (!setters.isEmpty()) {
                        // Transform this Flyway initialization
                        Statement declStmt = statements.get(declIndex);
                        Statement newDeclStmt = transformFlywayInitialization(declStmt, setters);
                        statements.set(declIndex, newDeclStmt);
                        
                        // Remove the setter statements
                        for (MethodCallExpr setter : setters) {
                            statements.removeIf(stmt -> 
                                stmt instanceof ExpressionStmt && 
                                ((ExpressionStmt) stmt).getExpression().equals(setter)
                            );
                        }
                        
                        modified[0] = true;
                    }
                }
                
                // Update the block with modified statements
                if (modified[0]) {
                    block.setStatements(new NodeList<>(statements));
                }
                
                return super.visit(block, arg);
            }
            
            private String extractFlywayVarName(Statement stmt) {
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                        String typeName = varDecl.getVariable(0).getType().asString();
                        // Match both simple and fully-qualified Flyway type
                        if (typeName.endsWith("Flyway")) {
                            return varDecl.getVariable(0).getNameAsString();
                        }
                    }
                }
                return null;
            }
            
            private Statement transformFlywayInitialization(Statement declStmt, List<MethodCallExpr> setters) {
                // Get the variable declaration
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) ((ExpressionStmt) declStmt).getExpression();
                String typeName = varDecl.getVariable(0).getType().asString();
                String flywayType = typeName.contains(".") ? typeName : "Flyway";
                
                // Check if we have setClassLoader - it should be passed to configure()
                Expression classLoaderArg = null;
                List<MethodCallExpr> otherSetters = new ArrayList<>();
                
                for (MethodCallExpr setter : setters) {
                    if (setter.getNameAsString().equals("setClassLoader")) {
                        classLoaderArg = setter.getArgument(0);
                    } else {
                        otherSetters.add(setter);
                    }
                }
                
                // Start building the fluent API chain
                MethodCallExpr configureCall;
                if (classLoaderArg != null) {
                    configureCall = new MethodCallExpr(
                        new NameExpr(flywayType), 
                        "configure",
                        new NodeList<>(classLoaderArg)
                    );
                } else {
                    configureCall = new MethodCallExpr(
                        new NameExpr(flywayType), 
                        "configure"
                    );
                }
                
                Expression fluentChain = configureCall;
                
                // Add each other setter as a fluent method call
                for (MethodCallExpr setter : otherSetters) {
                    String setterName = setter.getNameAsString();
                    String fluentMethod = SETTER_TO_FLUENT.get(setterName);
                    if (fluentMethod != null) {
                        NodeList<Expression> args = setter.getArguments();
                        fluentChain = new MethodCallExpr(fluentChain, fluentMethod, args);
                    }
                }
                
                // Add .load() at the end
                fluentChain = new MethodCallExpr(fluentChain, "load");
                
                // Create new variable declaration with the fluent chain
                varDecl.getVariable(0).setInitializer(fluentChain);
                return new ExpressionStmt(varDecl);
            }
        }, null);
        
        if (modified[0]) {
            Files.write(javaFile, cu.toString().getBytes());
        }
        
        return modified[0];
    }
}