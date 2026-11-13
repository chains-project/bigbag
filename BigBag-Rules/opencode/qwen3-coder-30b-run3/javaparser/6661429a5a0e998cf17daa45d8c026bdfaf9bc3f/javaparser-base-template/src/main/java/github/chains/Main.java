package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Generic JavaParser transformation rule for fixing breaking API changes in Maven projects.
 * This rule can be customized to handle specific breaking changes by adjusting the patterns
 * and replacement logic.
 */
public class Main {
    
    // The fully qualified name of the class that contains the broken method
    private static final String OLD_CLASS_FQN = "org.jenkinsci.test.acceptance.AbstractPipelineTest";
    
    // The method signature that changed (old form)
    private static final String OLD_METHOD_SIGNATURE = "assertJavadoc(org.jenkinsci.test.acceptance.po.Job p0)";
    
    // The new method signature (updated form) 
    private static final String NEW_METHOD_SIGNATURE = "assertJavadoc(org.jenkinsci.test.acceptance.po.Job p0, boolean p1)";
    
    // The replacement pattern - adds a new boolean parameter to the call
    private static final String REPLACEMENT_PATTERN = "$0(true)";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dirPath = Paths.get(sourceDir);
        
        if (!Files.exists(dirPath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            processDirectory(dirPath);
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path dirPath) throws IOException {
        Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation using a modifier visitor
            MethodCallTransformer transformer = new MethodCallTransformer();
            cu.accept(transformer, null);
            
            // Write back the modified content
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
        } catch (IOException e) {
            System.err.println("Failed to process file: " + filePath + " - " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms method calls matching our pattern
     */
    private static class MethodCallTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr methodCall, Void arg) {
            // Check if this is the method we want to transform
            if (methodCall.getScope().isPresent()) {
                String scope = methodCall.getScope().get().toString();
                String methodName = methodCall.getNameAsString();
                
                // Example: Check for calls to assertJavadoc in AbstractPipelineTest
                if (scope.contains(OLD_CLASS_FQN.replace(".", "/")) && 
                    methodName.equals("assertJavadoc") &&
                    methodCall.getArguments().size() == 1) {
                    
                    // Replace the method call with the new signature
                    return methodCall.clone()
                        .setArguments(Arrays.asList(
                            methodCall.getArgument(0),
                            StaticJavaParser.parseExpression("true") // Add new parameter
                        ));
                }
            }
            
            // Handle direct calls to the method if needed (no scope)
            if (methodName.equals("assertJavadoc") && 
                methodCall.getArguments().size() == 1) {
                
                // If the context makes it clear it's from AbstractPipelineTest, add the parameter
                return methodCall.clone()
                    .setArguments(Arrays.asList(
                        methodCall.getArgument(0),
                        StaticJavaParser.parseExpression("true") // Add new parameter
                    ));
            }
            
            return super.visit(methodCall, arg);
        }
    }
}
