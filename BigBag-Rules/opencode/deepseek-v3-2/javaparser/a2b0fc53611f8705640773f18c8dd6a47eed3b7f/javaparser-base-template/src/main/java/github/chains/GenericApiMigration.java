package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A generic API migration transformer that can be configured for different
 * breaking API changes.
 * 
 * This transformer handles the pattern:
 *   Old: Type var = new Type(); var.setMethod(args); ... return var;
 *   New: Type var = Type.configure().method(args).load();
 * 
 * Configuration parameters:
 * - targetType: The fully-qualified class name to transform (e.g., "org.flywaydb.core.Flyway")
 * - configureMethod: The static factory method (e.g., "configure")
 * - loadMethod: The final method to get instance (e.g., "load")  
 * - methodMappings: Map from old setter names to new builder method names
 */
public class GenericApiMigration {
    private final String targetType;
    private final String simpleTargetType;
    private final String configureMethod;
    private final String loadMethod;
    private final Map<String, String> methodMappings;
    
    public GenericApiMigration(String targetType, String configureMethod, 
                               String loadMethod, Map<String, String> methodMappings) {
        this.targetType = targetType;
        this.simpleTargetType = targetType.substring(targetType.lastIndexOf('.') + 1);
        this.configureMethod = configureMethod;
        this.loadMethod = loadMethod;
        this.methodMappings = methodMappings;
    }
    
    public void transformDirectory(Path sourceDir) throws IOException {
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedCount = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                modifiedCount++;
            }
        }
        
        System.out.println("Processing complete. Modified " + modifiedCount + " files.");
    }
    
    private boolean transformFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + filePath)
        );
        
        ApiMigrationVisitor visitor = new ApiMigrationVisitor();
        cu.accept(visitor, null);
        
        if (visitor.wasModified()) {
            Files.write(filePath, cu.toString().getBytes());
            return true;
        }
        return false;
    }
    
    class ApiMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private Map<String, List<MethodCallExpr>> variableSetters = new HashMap<>();
        private Map<String, String> variableTypes = new HashMap<>();
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            String typeName = n.getVariable(0).getType().asString();
            String varName = n.getVariable(0).getNameAsString();
            
            // Check if this is our target type
            boolean isTargetType = typeName.equals(simpleTargetType) || typeName.equals(targetType);
            
            if (isTargetType) {
                Optional<Expression> initializer = n.getVariable(0).getInitializer();
                if (initializer.isPresent() && initializer.get() instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creation = (ObjectCreationExpr) initializer.get();
                    String creationType = creation.getType().asString();
                    
                    if ((creationType.equals(simpleTargetType) || creationType.equals(targetType)) 
                        && creation.getArguments().isEmpty()) {
                        
                        modified = true;
                        variableTypes.put(varName, typeName);
                        variableSetters.put(varName, new ArrayList<>());
                        
                        // Replace new Type() with Type.configure()
                        String configureCall = typeName.equals(simpleTargetType) ? 
                            simpleTargetType + "." + configureMethod + "()" :
                            targetType + "." + configureMethod + "()";
                        
                        JavaParser parser = new JavaParser();
                        Expression configureExpr = parser.parseExpression(configureCall).getResult().orElseThrow();
                        n.getVariable(0).setInitializer(configureExpr);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            if (methodMappings.containsKey(methodName)) {
                Optional<Expression> scope = n.getScope();
                if (scope.isPresent() && scope.get() instanceof NameExpr) {
                    NameExpr nameExpr = (NameExpr) scope.get();
                    String varName = nameExpr.getNameAsString();
                    
                    if (variableSetters.containsKey(varName)) {
                        modified = true;
                        variableSetters.get(varName).add(n);
                        return null; // Remove this setter call
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            Visitable result = super.visit(n, arg);
            
            if (result == null) {
                return null;
            }
            
            // Check if this is a variable declaration with setters to rebuild
            if (n.getExpression() instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) n.getExpression();
                String varName = varDecl.getVariable(0).getNameAsString();
                
                if (variableSetters.containsKey(varName) && !variableSetters.get(varName).isEmpty()) {
                    rebuildWithBuilderPattern(varDecl, varName);
                    variableSetters.remove(varName);
                    variableTypes.remove(varName);
                }
            }
            
            return result;
        }
        
        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            Visitable result = super.visit(n, arg);
            
            // Check if returning a variable that needs .load()
            Optional<Expression> expr = n.getExpression();
            if (expr.isPresent() && expr.get() instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) expr.get();
                String varName = nameExpr.getNameAsString();
                
                if (variableTypes.containsKey(varName)) {
                    // This variable was transformed, need to add .load()
                    modified = true;
                    MethodCallExpr loadCall = new MethodCallExpr(nameExpr, loadMethod);
                    n.setExpression(loadCall);
                }
            }
            
            return result;
        }
        
        private void rebuildWithBuilderPattern(VariableDeclarationExpr varDecl, String varName) {
            Optional<Expression> initializerOpt = varDecl.getVariable(0).getInitializer();
            if (!initializerOpt.isPresent()) {
                return;
            }
            
            Expression currentExpr = initializerOpt.get();
            if (!(currentExpr instanceof MethodCallExpr)) {
                return;
            }
            
            MethodCallExpr builderChain = (MethodCallExpr) currentExpr;
            List<MethodCallExpr> setters = variableSetters.get(varName);
            
            for (MethodCallExpr setter : setters) {
                String oldMethodName = setter.getNameAsString();
                String newMethodName = methodMappings.get(oldMethodName);
                
                if (newMethodName != null) {
                    NodeList<Expression> args = setter.getArguments();
                    builderChain = new MethodCallExpr(builderChain, newMethodName, args);
                }
            }
            
            // Don't add .load() here - it will be added when the variable is returned
            varDecl.getVariable(0).setInitializer(builderChain);
        }
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.GenericApiMigration <source-directory>");
            System.err.println("Example configuration for Flyway 9.19.4 migration:");
            System.err.println("  - targetType: org.flywaydb.core.Flyway");
            System.err.println("  - configureMethod: configure");
            System.err.println("  - loadMethod: load");
            System.err.println("  - methodMappings: setDataSource->dataSource, setLocations->locations, setValidateOnMigrate->validateOnMigrate");
            System.exit(1);
        }
        
        // Configuration for Flyway 9.19.4 migration
        String targetType = "org.flywaydb.core.Flyway";
        String configureMethod = "configure";
        String loadMethod = "load";
        
        Map<String, String> methodMappings = new HashMap<>();
        methodMappings.put("setDataSource", "dataSource");
        methodMappings.put("setLocations", "locations");
        methodMappings.put("setValidateOnMigrate", "validateOnMigrate");
        methodMappings.put("setBaselineOnMigrate", "baselineOnMigrate");
        methodMappings.put("setBaselineVersion", "baselineVersion");
        methodMappings.put("setTable", "table");
        methodMappings.put("setEncoding", "encoding");
        // Note: setClassLoader is handled differently - passed to configure()
        
        GenericApiMigration transformer = new GenericApiMigration(
            targetType, configureMethod, loadMethod, methodMappings);
        
        Path sourceDir = Paths.get(args[0]);
        transformer.transformDirectory(sourceDir);
    }
}