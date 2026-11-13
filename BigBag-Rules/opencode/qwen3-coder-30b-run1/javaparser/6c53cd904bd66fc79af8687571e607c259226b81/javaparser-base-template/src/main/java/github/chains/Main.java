package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        // This is a generic transformation for fixing breaking changes in ActionProxyFactory
        // Usage: java -cp target/classes github.chains.Main <source_directory>
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        try {
            processDirectory(sourceDirectory);
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        Path directory = Paths.get(directoryPath);
        if (!Files.exists(directory)) {
            System.err.println("Directory does not exist: " + directoryPath);
            return;
        }
        
        List<Path> javaFiles = Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        for (Path file : javaFiles) {
            processJavaFile(file);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix ActionProxyFactory breaking changes
            new ActionProxyFactoryTransformation().visit(cu, null);
            
            // Print that we would modify the file (but not actually save for now)
            System.out.println("Processed file: " + filePath);
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class ActionProxyFactoryTransformation extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Look for calls to createActionProxy methods that have the old signature
            if (methodCall.getNameAsString().equals("createActionProxy")) {
                // Check if this is a call to ActionProxyFactory.createActionProxy
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    String scopeName = "";
                    
                    if (scope.isNameExpr()) {
                        scopeName = ((NameExpr) scope).getNameAsString();
                    } else if (scope.isFieldAccessExpr()) {
                        scopeName = ((FieldAccessExpr) scope).getNameAsString();
                    }
                    
                    // If it's a call to ActionProxyFactory.createActionProxy with 5 parameters
                    if (scopeName.equals("ActionProxyFactory") || 
                        (scope.isFieldAccessExpr() && 
                         ((FieldAccessExpr) scope).getScope().isNameExpr() && 
                         ((NameExpr) ((FieldAccessExpr) scope).getScope()).getNameAsString().equals("actionProxyFactory"))) {
                        
                        // Check the number of arguments and adjust if needed
                        NodeList<Expression> args = methodCall.getArguments();
                        if (args.size() == 5) {
                            // Transform from 5 args to 6 args by adding boolean parameters
                            // This is a common breaking change pattern
                            System.out.println("Found createActionProxy with 5 args - would add 2 boolean parameters");
                        }
                    }
                }
            }
            
            return super.visit(methodCall, arg);
        }
    }
}
