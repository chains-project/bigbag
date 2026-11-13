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
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Flyway API usage in: " + sourceDir);
        
        Path start = Paths.get(sourceDir);
        try (Stream<Path> paths = Files.walk(start)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        }
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path filePath) {
        try {
            ParseResult<CompilationUnit> parseResult = new JavaParser().parse(filePath);
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            FlywayTransformer transformer = new FlywayTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                // Clean up duplicate @Bean annotations
                String content = cu.toString();
                content = content.replaceAll("@Bean\\(initMethod = \"migrate\"\\)\\s+@Bean", "@Bean");
                
                Files.write(filePath, content.getBytes());
                System.out.println("Transformed: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(MethodDeclaration md, Void arg) {
            // First collect all information
            List<ObjectCreationExpr> flywayCreations = new ArrayList<>();
            Map<ObjectCreationExpr, String> variableNames = new HashMap<>();
            Map<String, List<MethodCallExpr>> settersByVar = new HashMap<>();
            
            // Find Flyway creations
            md.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ObjectCreationExpr expr, Void arg) {
                    String typeName = expr.getType().asString();
                    if ((typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway")) 
                        && expr.getArguments().isEmpty()) {
                        flywayCreations.add(expr);
                    }
                }
            }, null);
            
            if (flywayCreations.isEmpty()) {
                return super.visit(md, arg);
            }
            
            // Find variable names for each creation
            for (ObjectCreationExpr creation : flywayCreations) {
                String varName = findVariableName(creation);
                if (varName != null) {
                    variableNames.put(creation, varName);
                    settersByVar.put(varName, new ArrayList<>());
                }
            }
            
            // Find setters for each variable
            md.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr call, Void arg) {
                    if (call.getScope().isPresent() && call.getNameAsString().startsWith("set")) {
                        Expression scope = call.getScope().get();
                        if (scope.isNameExpr()) {
                            String varName = scope.asNameExpr().getNameAsString();
                            if (settersByVar.containsKey(varName)) {
                                settersByVar.get(varName).add(call);
                            }
                        }
                    }
                }
            }, null);
            
            // Transform each Flyway creation
            for (ObjectCreationExpr creation : flywayCreations) {
                String varName = variableNames.get(creation);
                if (varName != null && !settersByVar.get(varName).isEmpty()) {
                    modified = true;
                    
                    // Build new fluent API expression
                    Expression newExpr = buildFluentExpression(creation, settersByVar.get(varName));
                    
                    // Replace the creation
                    creation.replace(newExpr);
                    
                    // Remove all setters for this variable
                    for (MethodCallExpr setter : settersByVar.get(varName)) {
                        // Try to remove the entire statement containing the setter
                        Node parent = setter.getParentNode().orElse(null);
                        if (parent instanceof ExpressionStmt) {
                            ((ExpressionStmt) parent).remove();
                        } else {
                            setter.remove();
                        }
                    }
                }
            }
            
            return super.visit(md, arg);
        }
        
        private String findVariableName(ObjectCreationExpr expr) {
            Node parent = expr.getParentNode().orElse(null);
            if (parent instanceof VariableDeclarator) {
                return ((VariableDeclarator) parent).getNameAsString();
            } else if (parent instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr varExpr = (VariableDeclarationExpr) parent;
                if (!varExpr.getVariables().isEmpty()) {
                    return varExpr.getVariables().get(0).getNameAsString();
                }
            } else if (parent instanceof AssignExpr) {
                AssignExpr assign = (AssignExpr) parent;
                if (assign.getTarget().isNameExpr()) {
                    return assign.getTarget().asNameExpr().getNameAsString();
                }
            }
            return null;
        }
        
        private Expression buildFluentExpression(ObjectCreationExpr creation, List<MethodCallExpr> setters) {
            // Separate classLoader from other setters
            Expression classLoaderExpr = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                if (setter.getNameAsString().equals("setClassLoader")) {
                    classLoaderExpr = setter.getArgument(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            // Start with Flyway.configure() or Flyway.configure(classLoader)
            MethodCallExpr configureCall;
            if (classLoaderExpr != null) {
                configureCall = new MethodCallExpr(
                    new NameExpr("Flyway"), "configure", new NodeList<>(classLoaderExpr));
            } else {
                configureCall = new MethodCallExpr(
                    new NameExpr("Flyway"), "configure");
            }
            
            // Chain other setters
            MethodCallExpr currentCall = configureCall;
            for (MethodCallExpr setter : otherSetters) {
                String methodName = setter.getNameAsString();
                String fluentName = convertSetterToFluent(methodName);
                
                NodeList<Expression> args = new NodeList<>();
                args.addAll(setter.getArguments());
                currentCall = new MethodCallExpr(currentCall, fluentName, args);
            }
            
            // Add .load()
            return new MethodCallExpr(currentCall, "load");
        }
        
        private String convertSetterToFluent(String setterName) {
            // Known mappings
            Map<String, String> mappings = new HashMap<>();
            mappings.put("setDataSource", "dataSource");
            mappings.put("setLocations", "locations");
            mappings.put("setValidateOnMigrate", "validateOnMigrate");
            mappings.put("setClassLoader", "classLoader");
            
            if (mappings.containsKey(setterName)) {
                return mappings.get(setterName);
            }
            
            // Default: setXyz -> xyz
            if (setterName.startsWith("set") && setterName.length() > 3) {
                String base = setterName.substring(3);
                return Character.toLowerCase(base.charAt(0)) + base.substring(1);
            }
            
            return setterName;
        }
    }
}