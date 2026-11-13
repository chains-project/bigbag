package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    // Map of old setter method names to new fluent configuration method names
    private static final Map<String, String> METHOD_MAPPINGS = createMethodMappings();
    
    private static Map<String, String> createMethodMappings() {
        Map<String, String> mappings = new HashMap<>();
        // Core mappings based on the NEM project usage
        mappings.put("setDataSource", "dataSource");
        // setClassLoader is handled specially - passed to configure() method
        mappings.put("setLocations", "locations");
        mappings.put("setValidateOnMigrate", "validateOnMigrate");
        // Add other common mappings
        mappings.put("setBaselineDescription", "baselineDescription");
        mappings.put("setBaselineOnMigrate", "baselineOnMigrate");
        mappings.put("setBaselineVersion", "baselineVersion");
        mappings.put("setBaselineVersionAsString", "baselineVersion");
        mappings.put("setBatch", "batch");
        mappings.put("setCallbacks", "callbacks");
        mappings.put("setCleanDisabled", "cleanDisabled");
        mappings.put("setCleanOnValidationError", "cleanOnValidationError");
        mappings.put("setCreateSchemas", "createSchemas");
        mappings.put("setDefaultSchema", "defaultSchema");
        mappings.put("setFailOnMissingLocations", "failOnMissingLocations");
        mappings.put("setGroup", "group");
        mappings.put("setIgnoreMissingMigrations", "ignoreMissingMigrations");
        mappings.put("setIgnorePendingMigrations", "ignorePendingMigrations");
        mappings.put("setInstalledBy", "installedBy");
        mappings.put("setMixed", "mixed");
        mappings.put("setOutOfOrder", "outOfOrder");
        mappings.put("setPassword", "password");
        mappings.put("setPlaceholderReplacement", "placeholderReplacement");
        mappings.put("setSchemas", "schemas");
        mappings.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        mappings.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        mappings.put("setTable", "table");
        mappings.put("setTarget", "target");
        mappings.put("setUrl", "url");
        mappings.put("setUser", "user");
        mappings.put("setValidateMigrationNaming", "validateMigrationNaming");
        // Additional mappings for completeness
        mappings.put("setDataSource", "dataSource"); // Already have but being explicit
        mappings.put("setLocationsAsStrings", "locationsAsStrings");
        mappings.put("setEncoding", "encoding");
        mappings.put("setPlaceholderPrefix", "placeholderPrefix");
        mappings.put("setPlaceholderSuffix", "placeholderSuffix");
        mappings.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        mappings.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        mappings.put("setSqlMigrationSeparator", "sqlMigrationSeparator");
        mappings.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        mappings.put("setTarget", "target");
        mappings.put("setTargetAsString", "target");
        return mappings;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);

        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");

            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }

            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }

    private static boolean transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }

        FlywayTransformer transformer = new FlywayTransformer();
        cu.accept(transformer, null);

        if (transformer.wasTransformed()) {
            String transformedContent = cu.toString();
            Files.writeString(javaFile, transformedContent);
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }

    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }

        @Override
        public Visitable visit(BlockStmt n, Void arg) {
            // Process block statements to find Flyway creation patterns
            List<Statement> statements = n.getStatements();
            List<Statement> newStatements = new ArrayList<>();
            int i = 0;
            
            while (i < statements.size()) {
                Statement stmt = statements.get(i);
                
                // Check if this statement creates a Flyway instance
                FlywayCreationInfo creationInfo = findFlywayCreation(stmt);
                if (creationInfo != null) {
                    // Look ahead for setter calls on this variable
                    List<MethodCallExpr> setters = new ArrayList<>();
                    String varName = creationInfo.variableName;
                    int j = i + 1;
                    
                    while (j < statements.size()) {
                        Statement nextStmt = statements.get(j);
                        MethodCallExpr setter = findSetterOnVariable(nextStmt, varName);
                        if (setter != null) {
                            setters.add(setter);
                            j++;
                        } else {
                            break;
                        }
                    }
                    
                    if (!setters.isEmpty()) {
                        // Transform this block
                        transformed = true;
                        
                        // Create the new fluent configuration
                        Expression fluentConfig = createFluentConfiguration(creationInfo, setters);
                        
                        // Create new statement(s)
                        if (creationInfo.isReturnDirectly) {
                            // Case: return new Flyway() with setters after
                            // Need to handle specially - usually the return is at the end
                            // For now, we'll handle the common pattern
                        } else {
                            // Replace variable declaration with fluent configuration
                            Statement newStmt = createNewStatement(creationInfo, fluentConfig);
                            newStatements.add(newStmt);
                        }
                        
                        // Skip the setter statements (they're incorporated into the fluent chain)
                        i = j;
                        continue;
                    }
                }
                
                newStatements.add(stmt);
                i++;
            }
            
            if (transformed && !newStatements.equals(statements)) {
                n.setStatements(new NodeList<>(newStatements));
            }
            
            return super.visit(n, arg);
        }

        private FlywayCreationInfo findFlywayCreation(Statement stmt) {
            if (stmt instanceof ExpressionStmt) {
                Expression expr = ((ExpressionStmt) stmt).getExpression();
                
                if (expr instanceof VariableDeclarationExpr) {
                    VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                    for (com.github.javaparser.ast.body.VariableDeclarator declarator : varDecl.getVariables()) {
                        if (declarator.getInitializer().isPresent()) {
                            Expression initializer = declarator.getInitializer().get();
                            if (isFlywayCreation(initializer)) {
                                return new FlywayCreationInfo(
                                    declarator.getNameAsString(),
                                    initializer,
                                    varDecl,
                                    false
                                );
                            }
                        }
                    }
                } else if (expr instanceof AssignExpr) {
                    AssignExpr assign = (AssignExpr) expr;
                    if (assign.getTarget().isNameExpr() && isFlywayCreation(assign.getValue())) {
                        return new FlywayCreationInfo(
                            assign.getTarget().asNameExpr().getNameAsString(),
                            assign.getValue(),
                            assign,
                            false
                        );
                    }
                }
            } else if (stmt instanceof ReturnStmt) {
                ReturnStmt returnStmt = (ReturnStmt) stmt;
                if (returnStmt.getExpression().isPresent()) {
                    Expression expr = returnStmt.getExpression().get();
                    if (isFlywayCreation(expr)) {
                        // This handles: return new Flyway();
                        return new FlywayCreationInfo(
                            null, // no variable name
                            expr,
                            returnStmt.getExpression().get(),
                            true
                        );
                    }
                }
            }
            
            return null;
        }

        private boolean isFlywayCreation(Expression expr) {
            if (expr instanceof ObjectCreationExpr) {
                ObjectCreationExpr creation = (ObjectCreationExpr) expr;
                String typeName = creation.getTypeAsString();
                return ("Flyway".equals(typeName) || 
                        "org.flywaydb.core.Flyway".equals(typeName) ||
                        typeName.endsWith(".Flyway")) && 
                       creation.getArguments().isEmpty();
            }
            return false;
        }

        private MethodCallExpr findSetterOnVariable(Statement stmt, String varName) {
            if (stmt instanceof ExpressionStmt) {
                Expression expr = ((ExpressionStmt) stmt).getExpression();
                if (expr instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) expr;
                    String methodName = methodCall.getNameAsString();
                    
                    // Check if it's a setter we handle (either in METHOD_MAPPINGS or setClassLoader)
                    boolean isSetter = METHOD_MAPPINGS.containsKey(methodName) || 
                                     "setClassLoader".equals(methodName);
                    
                    if (isSetter && methodCall.getScope().isPresent()) {
                        Expression scope = methodCall.getScope().get();
                        if (scope.isNameExpr() && 
                            scope.asNameExpr().getNameAsString().equals(varName)) {
                            return methodCall;
                        }
                    }
                }
            }
            return null;
        }

        private Expression createFluentConfiguration(FlywayCreationInfo creationInfo, 
                                                    List<MethodCallExpr> setters) {
            // Start with Flyway.configure()
            MethodCallExpr configureCall;
            
            // Handle fully qualified or simple name
            if (creationInfo.creationExpr instanceof ObjectCreationExpr) {
                ObjectCreationExpr creation = (ObjectCreationExpr) creationInfo.creationExpr;
                String typeName = creation.getTypeAsString();
                
                // Determine if we need fully qualified name
                if (typeName.contains(".")) {
                    // Fully qualified name like org.flywaydb.core.Flyway
                    configureCall = new MethodCallExpr(
                        new NameExpr(typeName.substring(0, typeName.lastIndexOf('.')) + ".Flyway"),
                        "configure"
                    );
                } else {
                    // Simple name Flyway
                    configureCall = new MethodCallExpr(
                        new NameExpr("Flyway"),
                        "configure"
                    );
                }
            } else {
                // Fallback
                configureCall = new MethodCallExpr(
                    new NameExpr("Flyway"),
                    "configure"
                );
            }
            
            // Handle classLoader specially - it should be passed to configure() method
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                if ("setClassLoader".equals(setter.getNameAsString())) {
                    classLoaderArg = setter.getArgument(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            // If we have a classLoader argument, pass it to configure()
            Expression currentExpr;
            if (classLoaderArg != null) {
                // Static method call: Flyway.configure(classLoader)
                currentExpr = new MethodCallExpr(
                    configureCall.getScope().get(), // Get the scope (Flyway)
                    "configure",
                    new NodeList<>(classLoaderArg)
                );
            } else {
                currentExpr = configureCall;
            }
            
            // Add all other setter calls as fluent method calls
            for (MethodCallExpr setter : otherSetters) {
                String oldMethodName = setter.getNameAsString();
                String newMethodName = METHOD_MAPPINGS.get(oldMethodName);
                NodeList<Expression> arguments = setter.getArguments();
                
                currentExpr = new MethodCallExpr(currentExpr, newMethodName, arguments);
            }
            
            // Add .load() at the end
            return new MethodCallExpr(currentExpr, "load");
        }

        private Statement createNewStatement(FlywayCreationInfo creationInfo, Expression fluentConfig) {
            if (creationInfo.isReturnDirectly) {
                return new ReturnStmt(fluentConfig);
            } else if (creationInfo.originalExpr instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) creationInfo.originalExpr;
                for (com.github.javaparser.ast.body.VariableDeclarator declarator : varDecl.getVariables()) {
                    if (declarator.getNameAsString().equals(creationInfo.variableName)) {
                        declarator.setInitializer(fluentConfig);
                        break;
                    }
                }
                return new ExpressionStmt(varDecl);
            } else if (creationInfo.originalExpr instanceof AssignExpr) {
                AssignExpr assign = (AssignExpr) creationInfo.originalExpr;
                assign.setValue(fluentConfig);
                return new ExpressionStmt(assign);
            }
            
            // Should not reach here
            return new ExpressionStmt(fluentConfig);
        }

        private static class FlywayCreationInfo {
            final String variableName;
            final Expression creationExpr;
            final Expression originalExpr;
            final boolean isReturnDirectly;
            
            FlywayCreationInfo(String variableName, Expression creationExpr, 
                              Expression originalExpr, boolean isReturnDirectly) {
                this.variableName = variableName;
                this.creationExpr = creationExpr;
                this.originalExpr = originalExpr;
                this.isReturnDirectly = isReturnDirectly;
            }
        }
    }
}