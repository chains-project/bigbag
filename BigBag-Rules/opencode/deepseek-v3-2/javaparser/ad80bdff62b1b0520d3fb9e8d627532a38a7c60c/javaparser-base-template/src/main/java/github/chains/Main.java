package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.*;
import com.github.javaparser.resolution.*;
import com.github.javaparser.symbolsolver.*;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformedFiles + " file(s).");
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        ParseResult<CompilationUnit> parseResult = new JavaParser().parse(content);
        
        if (!parseResult.isSuccessful()) {
            System.err.println("Warning: Could not parse " + filePath + ", skipping.");
            return false;
        }
        
        CompilationUnit cu = parseResult.getResult().orElseThrow();
        FlywayTransformer transformer = new FlywayTransformer();
        transformer.visit(cu, null);
        boolean modified = transformer.isModified();
        
        if (modified) {
            Files.writeString(filePath, cu.toString());
            System.out.println("Transformed: " + filePath);
            return true;
        }
        
        return false;
    }
}

class FlywayTransformer extends VoidVisitorAdapter<Void> {
    private boolean modified = false;
    
    public boolean isModified() {
        return modified;
    }
    
    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        super.visit(n, arg);
        
        try {
            if (isFlywayConstructorCall(n)) {
                transformFlywayCreation(n);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error transforming Flyway creation at " + n.getRange().orElse(null) + ": " + e.getMessage());
        }
    }
    
    private boolean isFlywayConstructorCall(ObjectCreationExpr expr) {
        String typeName = expr.getTypeAsString();
        return typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway");
    }
    
    private void transformFlywayCreation(ObjectCreationExpr expr) {
        Node parent = expr.getParentNode().orElse(null);
        
        if (!(parent instanceof VariableDeclarationExpr) && !(parent instanceof VariableDeclarator)) {
            return;
        }
        
        VariableDeclarator variableDeclarator = null;
        if (parent instanceof VariableDeclarator) {
            variableDeclarator = (VariableDeclarator) parent;
        } else if (parent instanceof VariableDeclarationExpr) {
            VariableDeclarationExpr varDeclExpr = (VariableDeclarationExpr) parent;
            if (!varDeclExpr.getVariables().isEmpty()) {
                variableDeclarator = varDeclExpr.getVariables().get(0);
            }
        }
        
        if (variableDeclarator == null) {
            return;
        }
        
        String variableName = variableDeclarator.getNameAsString();
        
        Node scopeRoot = findScopeRoot(expr);
        if (scopeRoot == null) {
            return;
        }
        
        List<MethodCallExpr> setterCalls = findSetterCalls(scopeRoot, variableName);
        
        if (!setterCalls.isEmpty()) {
            transformToFluentConfiguration(expr, variableDeclarator, setterCalls);
        }
    }
    
    private Node findScopeRoot(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof BlockStmt || 
                current instanceof MethodDeclaration || 
                current instanceof InitializerDeclaration ||
                current instanceof LambdaExpr) {
                return current;
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }
    
    private List<MethodCallExpr> findSetterCalls(Node scopeRoot, String variableName) {
        List<MethodCallExpr> setterCalls = new ArrayList<>();
        scopeRoot.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                
                if (n.getScope().isPresent()) {
                    Expression scope = n.getScope().get();
                    if (scope instanceof NameExpr) {
                        NameExpr nameExpr = (NameExpr) scope;
                        if (nameExpr.getNameAsString().equals(variableName)) {
                            String methodName = n.getNameAsString();
                            if (methodName.startsWith("set") && n.getArguments().size() == 1) {
                                setterCalls.add(n);
                            }
                        }
                    }
                }
            }
        }, null);
        
        return setterCalls;
    }
    
    private void transformToFluentConfiguration(ObjectCreationExpr expr, 
                                               VariableDeclarator variableDeclarator, 
                                               List<MethodCallExpr> setterCalls) {
        
        Node parent = variableDeclarator.getParentNode().orElse(null);
        if (!(parent instanceof VariableDeclarationExpr)) {
            return;
        }
        
        VariableDeclarationExpr varDeclExpr = (VariableDeclarationExpr) parent;
        Node varDeclParent = varDeclExpr.getParentNode().orElse(null);
        
        if (!(varDeclParent instanceof ExpressionStmt)) {
            return;
        }
        
        ExpressionStmt varDeclStmt = (ExpressionStmt) varDeclParent;
        Node block = varDeclStmt.getParentNode().orElse(null);
        
        if (!(block instanceof BlockStmt)) {
            return;
        }
        
        BlockStmt blockStmt = (BlockStmt) block;
        int varDeclIndex = blockStmt.getStatements().indexOf(varDeclStmt);
        
        String configVarName = variableDeclarator.getNameAsString() + "Config";
        
        StringBuilder configCode = new StringBuilder();
        configCode.append("org.flywaydb.core.api.configuration.FluentConfiguration ")
                  .append(configVarName)
                  .append(" = org.flywaydb.core.Flyway.configure();\n");
        
        Map<String, String> methodMap = createMethodMap();
        
        for (MethodCallExpr setterCall : setterCalls) {
            String methodName = setterCall.getNameAsString();
            String fluentMethod = methodMap.get(methodName);
            
            if (fluentMethod != null) {
                Expression arg = setterCall.getArgument(0);
                configCode.append(configVarName).append(".").append(fluentMethod).append("(");
                
                if (arg instanceof BooleanLiteralExpr) {
                    configCode.append(((BooleanLiteralExpr)arg).getValue());
                } else if (arg instanceof StringLiteralExpr) {
                    configCode.append("\"").append(((StringLiteralExpr)arg).getValue()).append("\"");
                } else {
                    configCode.append(arg.toString());
                }
                
                configCode.append(");\n");
            }
        }
        
        configCode.append("org.flywaydb.core.Flyway ")
                  .append(variableDeclarator.getNameAsString())
                  .append(" = ")
                  .append(configVarName)
                  .append(".load();");
        
        try {
            ParseResult<Statement> configParseResult = new JavaParser().parseStatement(configCode.toString());
            if (configParseResult.isSuccessful()) {
                Statement configStmt = configParseResult.getResult().orElseThrow();
                
                blockStmt.getStatements().remove(varDeclStmt);
                blockStmt.addStatement(varDeclIndex, configStmt);
                
                for (MethodCallExpr setterCall : setterCalls) {
                    setterCall.remove();
                }
                
                modified = true;
            }
        } catch (Exception e) {
            System.err.println("Error parsing generated configuration code: " + e.getMessage());
        }
    }
    
    private Map<String, String> createMethodMap() {
        Map<String, String> map = new HashMap<>();
        map.put("setDataSource", "dataSource");
        map.put("setClassLoader", "classLoader");
        map.put("setLocations", "locations");
        map.put("setValidateOnMigrate", "validateOnMigrate");
        map.put("setBaselineOnMigrate", "baselineOnMigrate");
        map.put("setBaselineVersion", "baselineVersion");
        map.put("setTable", "table");
        map.put("setSchemas", "schemas");
        map.put("setEncoding", "encoding");
        map.put("setPlaceholders", "placeholders");
        map.put("setPlaceholderPrefix", "placeholderPrefix");
        map.put("setPlaceholderSuffix", "placeholderSuffix");
        map.put("setSqlMigrationPrefix", "sqlMigrationPrefix");
        map.put("setSqlMigrationSuffixes", "sqlMigrationSuffixes");
        map.put("setRepeatableSqlMigrationPrefix", "repeatableSqlMigrationPrefix");
        map.put("setCleanOnValidationError", "cleanOnValidationError");
        map.put("setCleanDisabled", "cleanDisabled");
        map.put("setOutOfOrder", "outOfOrder");
        map.put("setTarget", "target");
        map.put("setInitSql", "initSql");
        map.put("setInstalledBy", "installedBy");
        return map;
    }
}