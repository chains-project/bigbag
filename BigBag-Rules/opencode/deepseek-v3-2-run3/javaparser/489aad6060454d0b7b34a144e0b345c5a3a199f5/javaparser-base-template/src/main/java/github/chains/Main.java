package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(content).getResult();
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        CompilationUnit cu = cuOpt.get();
        FlywayTransformer transformer = new FlywayTransformer();
        CompilationUnit transformedCu = (CompilationUnit) transformer.visit(cu, null);
        
        if (transformer.wasTransformed()) {
            String transformedContent = transformedCu.toString();
            Files.write(javaFile, transformedContent.getBytes());
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        private Map<String, List<MethodCallExpr>> variableSetters = new HashMap<>();
        private Map<String, ObjectCreationExpr> variableCreations = new HashMap<>();
        private Map<String, List<ExpressionStmt>> settersToRemove = new HashMap<>();
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Check if this is "new Flyway()"
            String typeName = expr.getType().toString();
            if (typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway")) {
                if (expr.getArguments().isEmpty()) {
                    // System.out.println("Found Flyway constructor: " + typeName);
                    return expr;
                }
            }
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr expr, Void arg) {
            for (var declarator : expr.getVariables()) {
                if (declarator.getInitializer().isPresent()) {
                    Expression init = declarator.getInitializer().get();
                    if (init instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creation = (ObjectCreationExpr) init;
                        String typeName = creation.getType().toString();
                        if (typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway")) {
                            if (creation.getArguments().isEmpty()) {
                                variableCreations.put(declarator.getNameAsString(), creation);
                            }
                        }
                    }
                }
            }
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr expr, Void arg) {
            if (expr.getScope().isPresent() && expr.getScope().get() instanceof NameExpr) {
                NameExpr scope = (NameExpr) expr.getScope().get();
                String varName = scope.getNameAsString();
                String methodName = expr.getNameAsString();
                
                if (methodName.startsWith("set") && expr.getArguments().size() == 1) {
                    if (!variableSetters.containsKey(varName)) {
                        variableSetters.put(varName, new ArrayList<>());
                    }
                    variableSetters.get(varName).add(expr);
                }
            }
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt stmt, Void arg) {
            // Check if this is a setter statement for a Flyway variable
            if (stmt.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr expr = (MethodCallExpr) stmt.getExpression();
                if (expr.getScope().isPresent() && expr.getScope().get() instanceof NameExpr) {
                    NameExpr scope = (NameExpr) expr.getScope().get();
                    String varName = scope.getNameAsString();
                    String methodName = expr.getNameAsString();
                    
                    if (methodName.startsWith("set") && expr.getArguments().size() == 1) {
                        // This is a setter statement
                        if (!settersToRemove.containsKey(varName)) {
                            settersToRemove.put(varName, new ArrayList<>());
                        }
                        settersToRemove.get(varName).add(stmt);
                    }
                }
            }
            return super.visit(stmt, arg);
        }
        
        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            // First collect all information
            Visitable result = super.visit(cu, arg);
            
            // Now apply transformations
            for (String varName : variableCreations.keySet()) {
                if (variableSetters.containsKey(varName)) {
                    applyTransformation(varName);
                }
            }
            
            return result;
        }
        
        private void applyTransformation(String varName) {
            ObjectCreationExpr creation = variableCreations.get(varName);
            List<MethodCallExpr> setters = variableSetters.getOrDefault(varName, new ArrayList<>());
            
            // Create the new Flyway.configure() expression
            Expression configureCall;
            
            // Check if setClassLoader is used
            Optional<MethodCallExpr> classLoaderSetter = setters.stream()
                .filter(m -> m.getNameAsString().equals("setClassLoader"))
                .findFirst();
            
            if (classLoaderSetter.isPresent()) {
                Expression classLoaderArg = classLoaderSetter.get().getArgument(0);
                configureCall = new MethodCallExpr(
                    new NameExpr("Flyway"), 
                    "configure", 
                    new NodeList<>(classLoaderArg)
                );
                setters.remove(classLoaderSetter.get());
            } else {
                configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            }
            
            // Build the fluent chain
            Expression fluentChain = configureCall;
            
            for (MethodCallExpr setter : setters) {
                String setterName = setter.getNameAsString();
                String fluentMethodName = mapSetterToFluent(setterName);
                
                if (fluentMethodName != null) {
                    Expression arg = setter.getArgument(0);
                    fluentChain = new MethodCallExpr(fluentChain, fluentMethodName, new NodeList<>(arg));
                }
            }
            
            // Add .load() at the end
            fluentChain = new MethodCallExpr(fluentChain, "load");
            
            transformed = true;
            
            // Replace the creation expression
            creation.replace(fluentChain);
            
            // Remove setter statements
            if (settersToRemove.containsKey(varName)) {
                for (ExpressionStmt stmt : settersToRemove.get(varName)) {
                    // Find and remove this statement
                    Node parent = stmt.getParentNode().orElse(null);
                    if (parent instanceof BlockStmt) {
                        BlockStmt block = (BlockStmt) parent;
                        block.getStatements().remove(stmt);
                    }
                }
            }
        }
        
        private String mapSetterToFluent(String setterName) {
            if (setterName.startsWith("set") && setterName.length() > 3) {
                String baseName = setterName.substring(3);
                return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
            }
            return null;
        }
    }
}