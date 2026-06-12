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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = new JavaParser().parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                FlywayMigrationVisitor visitor = new FlywayMigrationVisitor();
                cu.accept(visitor, null);
                
                int transformations = visitor.getTransformations();
                if (transformations > 0) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    totalTransformations += transformations;
                    System.out.println("Transformed " + transformations + " patterns in: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    static class FlywayMigrationVisitor extends ModifierVisitor<Void> {
        private int transformations = 0;
        
        public int getTransformations() {
            return transformations;
        }
        
        @Override
        public Visitable visit(MethodDeclaration method, Void arg) {
            // Look for methods that create Flyway instances
            BlockStmt body = method.getBody().orElse(null);
            if (body == null) {
                return super.visit(method, arg);
            }
            
            // Pattern 1: Flyway variable declaration with new Flyway() followed by setters
            List<Statement> statements = body.getStatements();
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (stmt instanceof ExpressionStmt) {
                    Expression expr = ((ExpressionStmt) stmt).getExpression();
                    if (expr instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                        for (VariableDeclarator vd : varDecl.getVariables()) {
                            String typeName = vd.getType().asString();
                            if (("Flyway".equals(typeName) || "org.flywaydb.core.Flyway".equals(typeName)) 
                                && vd.getInitializer().isPresent()) {
                                
                                Expression initializer = vd.getInitializer().get();
                                if (initializer instanceof ObjectCreationExpr) {
                                    ObjectCreationExpr creationExpr = (ObjectCreationExpr) initializer;
                                    if (creationExpr.getArguments().isEmpty()) {
                                        // Found: Flyway flyway = new Flyway();
                                        String varName = vd.getNameAsString();
                                        
                                        // Collect subsequent setter calls
                                        List<MethodCallExpr> setters = new ArrayList<>();
                                        List<Integer> setterIndices = new ArrayList<>();
                                        
                                        for (int j = i + 1; j < statements.size(); j++) {
                                            Statement nextStmt = statements.get(j);
                                            if (nextStmt instanceof ExpressionStmt) {
                                                Expression nextExpr = ((ExpressionStmt) nextStmt).getExpression();
                                                if (nextExpr instanceof MethodCallExpr) {
                                                    MethodCallExpr methodCall = (MethodCallExpr) nextExpr;
                                                    if (isOldFlywaySetter(methodCall.getNameAsString())) {
                                                        Expression scope = methodCall.getScope().orElse(null);
                                                        if (scope instanceof NameExpr && 
                                                            varName.equals(((NameExpr) scope).getNameAsString())) {
                                                            setters.add(methodCall);
                                                            setterIndices.add(j);
                                                        } else {
                                                            // Not a setter on our variable, break
                                                            break;
                                                        }
                                                    } else {
                                                        // Not a setter, break
                                                        break;
                                                    }
                                                } else if (nextStmt instanceof ReturnStmt) {
                                                    // Check if this returns our variable
                                                    ReturnStmt returnStmt = (ReturnStmt) nextStmt;
                                                    if (returnStmt.getExpression().isPresent()) {
                                                        Expression retExpr = returnStmt.getExpression().get();
                                                        if (retExpr instanceof NameExpr && 
                                                            varName.equals(((NameExpr) retExpr).getNameAsString())) {
                                                            // This is the pattern we want to transform
                                                            transformations++;
                                                            
                                                            // Build new fluent configuration
                                                            String flywayType = vd.getType().asString();
                                                            boolean isFullyQualified = flywayType.contains(".");
                                                            String configureType = isFullyQualified ? "org.flywaydb.core.Flyway" : "Flyway";
                                                            
                                                            // Start with Flyway.configure()
                                                            MethodCallExpr configureCall = new MethodCallExpr(
                                                                new NameExpr(configureType),
                                                                "configure"
                                                            );
                                                            
                                                            // Apply configurations
                                                            Expression fluentChain = configureCall;
                                                            for (MethodCallExpr setter : setters) {
                                                                String oldMethod = setter.getNameAsString();
                                                                String newMethod = mapToFluentMethod(oldMethod);
                                                                
                                                                // Skip classLoader setter
                                                                if ("setClassLoader".equals(oldMethod)) {
                                                                    continue;
                                                                }
                                                                
                                                                NodeList<Expression> arguments = setter.getArguments();
                                                                fluentChain = new MethodCallExpr(fluentChain, newMethod, arguments);
                                                            }
                                                            
                                                            // Add .load()
                                                            fluentChain = new MethodCallExpr(fluentChain, "load");
                                                            
                                                            // Replace the return statement with direct return
                                                            returnStmt.setExpression(fluentChain);
                                                            
                                                            // Remove the variable declaration and setter statements
                                                            List<Statement> newStatements = new ArrayList<>();
                                                            for (int k = 0; k < statements.size(); k++) {
                                                                if (k == i) continue; // Skip variable declaration
                                                                if (setterIndices.contains(k)) continue; // Skip setters
                                                                newStatements.add(statements.get(k));
                                                            }
                                                            
                                                            body.setStatements(new NodeList<>(newStatements));
                                                            return super.visit(method, arg);
                                                        }
                                                    }
                                                } else {
                                                    // Not a setter or return, break
                                                    break;
                                                }
                                            } else {
                                                // Not an expression statement, break
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            return super.visit(method, arg);
        }
        
        private boolean isOldFlywaySetter(String methodName) {
            return methodName.startsWith("set") && (
                "setDataSource".equals(methodName) ||
                "setClassLoader".equals(methodName) ||
                "setLocations".equals(methodName) ||
                "setValidateOnMigrate".equals(methodName) ||
                "setBaselineVersion".equals(methodName) ||
                "setBaselineDescription".equals(methodName) ||
                "setBaselineOnMigrate".equals(methodName) ||
                "setPlaceholders".equals(methodName) ||
                "setPlaceholderPrefix".equals(methodName) ||
                "setPlaceholderSuffix".equals(methodName) ||
                "setSqlMigrationPrefix".equals(methodName) ||
                "setRepeatableSqlMigrationPrefix".equals(methodName) ||
                "setSqlMigrationSeparator".equals(methodName) ||
                "setSqlMigrationSuffixes".equals(methodName) ||
                "setTarget".equals(methodName) ||
                "setTable".equals(methodName) ||
                "setSchemas".equals(methodName) ||
                "setCleanDisabled".equals(methodName) ||
                "setCleanOnValidationError".equals(methodName) ||
                "setEncoding".equals(methodName) ||
                "setOutOfOrder".equals(methodName) ||
                "setMixed".equals(methodName) ||
                "setGroup".equals(methodName) ||
                "setInstalledBy".equals(methodName) ||
                "setPlaceholderReplacement".equals(methodName) ||
                "setValidateMigrationNaming".equals(methodName)
            );
        }
        
        private String mapToFluentMethod(String oldSetter) {
            if (oldSetter.startsWith("set")) {
                String baseName = oldSetter.substring(3);
                
                // Special mappings
                Map<String, String> specialMappings = new HashMap<>();
                specialMappings.put("ValidateOnMigrate", "validateOnMigrate");
                specialMappings.put("DataSource", "dataSource");
                specialMappings.put("Locations", "locations");
                specialMappings.put("BaselineVersion", "baselineVersion");
                specialMappings.put("BaselineDescription", "baselineDescription");
                specialMappings.put("BaselineOnMigrate", "baselineOnMigrate");
                specialMappings.put("Placeholders", "placeholders");
                specialMappings.put("PlaceholderPrefix", "placeholderPrefix");
                specialMappings.put("PlaceholderSuffix", "placeholderSuffix");
                specialMappings.put("SqlMigrationPrefix", "sqlMigrationPrefix");
                specialMappings.put("RepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
                specialMappings.put("SqlMigrationSeparator", "sqlMigrationSeparator");
                specialMappings.put("SqlMigrationSuffixes", "sqlMigrationSuffixes");
                specialMappings.put("Target", "target");
                specialMappings.put("Table", "table");
                specialMappings.put("Schemas", "schemas");
                specialMappings.put("CleanDisabled", "cleanDisabled");
                specialMappings.put("CleanOnValidationError", "cleanOnValidationError");
                specialMappings.put("Encoding", "encoding");
                specialMappings.put("OutOfOrder", "outOfOrder");
                specialMappings.put("Mixed", "mixed");
                specialMappings.put("Group", "group");
                specialMappings.put("InstalledBy", "installedBy");
                specialMappings.put("PlaceholderReplacement", "placeholderReplacement");
                specialMappings.put("ValidateMigrationNaming", "validateMigrationNaming");
                
                if (specialMappings.containsKey(baseName)) {
                    return specialMappings.get(baseName);
                }
                
                // Default mapping: setFooBar -> fooBar
                return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
            }
            return oldSetter;
        }
    }
}