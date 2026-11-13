package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
        if (cu == null) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        // Configuration for Flyway migration
        ApiMigrationConfig flywayConfig = new ApiMigrationConfig(
            "Flyway", // className
            "configure", // factoryMethod
            "load", // terminalMethod
            Arrays.asList("setClassLoader"), // constructorSetters
            Map.of(
                "setDataSource", "dataSource",
                "setLocations", "locations", 
                "setValidateOnMigrate", "validateOnMigrate"
            ) // setterToFluentMap
        );
        
        ApiMigrationTransformer transformer = new ApiMigrationTransformer(flywayConfig);
        cu.accept(transformer, null);
        
        if (transformer.isModified()) {
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    /**
     * Configuration for an API migration from constructor+setters pattern
     * to factory+fluent+terminal pattern.
     */
    private static class ApiMigrationConfig {
        final String className;
        final String factoryMethod;
        final String terminalMethod;
        final Set<String> constructorSetters;
        final Map<String, String> setterToFluentMap;
        
        ApiMigrationConfig(String className, String factoryMethod, String terminalMethod,
                          List<String> constructorSetters, Map<String, String> setterToFluentMap) {
            this.className = className;
            this.factoryMethod = factoryMethod;
            this.terminalMethod = terminalMethod;
            this.constructorSetters = new HashSet<>(constructorSetters);
            this.setterToFluentMap = setterToFluentMap;
        }
        
        boolean isTargetClass(String typeName) {
            return typeName.equals(className) || typeName.endsWith("." + className);
        }
        
        boolean isRelevantSetter(String methodName) {
            return constructorSetters.contains(methodName) || 
                   setterToFluentMap.containsKey(methodName);
        }
        
        String getFluentMethod(String setterName) {
            return setterToFluentMap.get(setterName);
        }
        
        boolean isConstructorSetter(String setterName) {
            return constructorSetters.contains(setterName);
        }
    }
    
    /**
     * Transformer for migrating APIs from constructor+setters pattern
     * to factory+fluent+terminal pattern.
     */
    private static class ApiMigrationTransformer extends ModifierVisitor<Void> {
        private final ApiMigrationConfig config;
        private boolean modified = false;
        
        // Track variable declarations and their setter calls
        private static class VariableInfo {
            VariableDeclarator declaration;
            List<MethodCallExpr> setters = new ArrayList<>();
        }
        
        private Map<String, VariableInfo> variableMap = new HashMap<>();
        
        ApiMigrationTransformer(ApiMigrationConfig config) {
            this.config = config;
        }
        
        @Override
        public Visitable visit(com.github.javaparser.ast.expr.VariableDeclarationExpr n, Void arg) {
            // Look for variable declarations of the target type
            for (VariableDeclarator vd : n.getVariables()) {
                if (vd.getInitializer().isPresent()) {
                    Expression init = vd.getInitializer().get();
                    if (init instanceof ObjectCreationExpr) {
                        ObjectCreationExpr oce = (ObjectCreationExpr) init;
                        if (config.isTargetClass(oce.getType().toString())) {
                            String varName = vd.getNameAsString();
                            VariableInfo info = new VariableInfo();
                            info.declaration = vd;
                            variableMap.put(varName, info);
                            modified = true;
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check for setter calls on tracked variables
            if (n.getNameAsString().startsWith("set") && n.getScope().isPresent()) {
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String varName = ((NameExpr) scope).getNameAsString();
                    VariableInfo info = variableMap.get(varName);
                    if (info != null && config.isRelevantSetter(n.getNameAsString())) {
                        // Record this setter call
                        info.setters.add(n);
                        modified = true;
                        // Remove the setter statement
                        return null;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            // Check if returning a tracked variable
            if (n.getExpression().isPresent()) {
                Expression expr = n.getExpression().get();
                if (expr instanceof NameExpr) {
                    String varName = ((NameExpr) expr).getNameAsString();
                    VariableInfo info = variableMap.get(varName);
                    if (info != null && !info.setters.isEmpty()) {
                        // Transform the variable declaration
                        transformVariable(info);
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        private void transformVariable(VariableInfo info) {
            // Separate constructor setters from fluent setters
            Map<String, Expression> constructorArgs = new HashMap<>();
            List<MethodCallExpr> fluentSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : info.setters) {
                String setName = setter.getNameAsString();
                if (config.isConstructorSetter(setName) && !setter.getArguments().isEmpty()) {
                    constructorArgs.put(setName, setter.getArgument(0));
                } else {
                    fluentSetters.add(setter);
                }
            }
            
            // Build factory method call
            MethodCallExpr factoryCall;
            if (!constructorArgs.isEmpty()) {
                // Use constructor args - for Flyway: configure(classLoader)
                // Assuming first constructor arg is used
                Expression firstArg = constructorArgs.values().iterator().next();
                factoryCall = new MethodCallExpr(
                    new NameExpr(config.className), config.factoryMethod, 
                    new com.github.javaparser.ast.NodeList<>(firstArg));
            } else {
                factoryCall = new MethodCallExpr(
                    new NameExpr(config.className), config.factoryMethod);
            }
            
            // Build fluent chain
            MethodCallExpr currentChain = factoryCall;
            for (MethodCallExpr setter : fluentSetters) {
                String fluentMethod = config.getFluentMethod(setter.getNameAsString());
                if (fluentMethod != null) {
                    MethodCallExpr fluentCall = new MethodCallExpr(currentChain, fluentMethod);
                    fluentCall.setArguments(setter.getArguments());
                    currentChain = fluentCall;
                }
            }
            
            // Add terminal method
            MethodCallExpr terminalCall = new MethodCallExpr(currentChain, config.terminalMethod);
            
            // Update variable initializer
            info.declaration.setInitializer(terminalCall);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}