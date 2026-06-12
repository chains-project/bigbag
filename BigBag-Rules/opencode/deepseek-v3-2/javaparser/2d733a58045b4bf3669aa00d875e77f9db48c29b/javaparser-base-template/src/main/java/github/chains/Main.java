package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            FlywayTransformer transformer = new FlywayTransformer();
            cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String newContent = printer.print(cu);
                Files.writeString(filePath, newContent);
                System.out.println("Modified: " + filePath);
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
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a new Flyway() constructor call
            if (isFlywayConstructor(n)) {
                System.out.println("Found Flyway constructor at line: " + n.getRange().map(r -> r.begin.line).orElse(-1));
                
                // Collect subsequent method calls on this Flyway instance
                List<MethodCallExpr> setters = collectSetters(n);
                
                if (!setters.isEmpty()) {
                    System.out.println("Found " + setters.size() + " setter calls to transform");
                    
                    // Transform to fluent API
                    Expression newExpression = transformToFluentAPI(setters);
                    
                    // Replace the entire expression chain
                    Node parent = n.getParentNode().orElse(null);
                    if (parent instanceof MethodCallExpr) {
                        // If the constructor is part of a method call chain, we need to replace the whole chain
                        MethodCallExpr lastCall = setters.get(setters.size() - 1);
                        lastCall.replace(newExpression);
                    } else {
                        // Just replace the constructor and its setters
                        n.replace(newExpression);
                    }
                    
                    modified = true;
                    return newExpression;
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isFlywayConstructor(ObjectCreationExpr expr) {
            String typeName = expr.getType().asString();
            return typeName.equals("Flyway") || typeName.equals(FLYWAY_FQN);
        }
        
        private List<MethodCallExpr> collectSetters(ObjectCreationExpr constructor) {
            List<MethodCallExpr> setters = new ArrayList<>();
            
            Node current = constructor;
            while (current != null) {
                Optional<Node> parentOpt = current.getParentNode();
                if (!parentOpt.isPresent()) {
                    break;
                }
                
                Node parent = parentOpt.get();
                if (parent instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) parent;
                    String methodName = methodCall.getNameAsString();
                    
                    // Check if this is a setter method (starts with "set")
                    if (methodName.startsWith("set")) {
                        setters.add(methodCall);
                        current = parent;
                        continue;
                    }
                }
                break;
            }
            
            return setters;
        }
        
        private Expression transformToFluentAPI(List<MethodCallExpr> setters) {
            // Check if there's a setClassLoader call - it should be passed to configure()
            Expression classLoaderArg = null;
            List<MethodCallExpr> otherSetters = new ArrayList<>();
            
            for (MethodCallExpr setter : setters) {
                String setterName = setter.getNameAsString();
                if (setterName.equals("setClassLoader")) {
                    // setClassLoader should be passed to configure() method
                    classLoaderArg = setter.getArgument(0);
                } else {
                    otherSetters.add(setter);
                }
            }
            
            // Start with Flyway.configure() - with or without classloader argument
            String configureMethod = "Flyway.configure";
            MethodCallExpr configureCall;
            
            if (classLoaderArg != null) {
                configureCall = new MethodCallExpr(null, configureMethod);
                configureCall.addArgument(classLoaderArg.clone());
            } else {
                configureCall = new MethodCallExpr(null, configureMethod);
            }
            
            // For each setter, convert to fluent method call
            Expression currentExpression = configureCall;
            
            for (MethodCallExpr setter : otherSetters) {
                String setterName = setter.getNameAsString();
                String fluentName = convertSetterToFluentName(setterName);
                
                MethodCallExpr fluentCall = new MethodCallExpr(currentExpression, fluentName);
                
                // Copy arguments from setter to fluent call
                setter.getArguments().forEach(arg -> fluentCall.addArgument(arg.clone()));
                
                currentExpression = fluentCall;
            }
            
            // Add .load() at the end
            MethodCallExpr loadCall = new MethodCallExpr(currentExpression, "load");
            
            return loadCall;
        }
        
        private String convertSetterToFluentName(String setterName) {
            // Remove "set" prefix and lowercase first letter
            if (setterName.startsWith("set")) {
                String baseName = setterName.substring(3);
                // Convert to camelCase: setValidateOnMigrate -> validateOnMigrate
                return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
            }
            return setterName;
        }
    }
}