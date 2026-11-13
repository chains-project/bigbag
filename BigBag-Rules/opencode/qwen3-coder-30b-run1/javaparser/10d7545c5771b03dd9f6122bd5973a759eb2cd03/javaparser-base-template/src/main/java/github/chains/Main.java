package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.NodeList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDirectory);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            boolean modified = cu.accept(new JerseyClientBuilderTransformationVisitor(), null);
            
            if (modified) {
                // Save the modified file
                cu.toFile(filePath.toFile());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class JerseyClientBuilderTransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Expression visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a JerseyClientBuilder constructor call with Environment parameter
            if (n.getType().getNameAsString().equals("JerseyClientBuilder")) {
                // Check if it's from the dropwizard client package
                String fullyQualifiedName = n.getType().asString();
                if (fullyQualifiedName.equals("io.dropwizard.client.JerseyClientBuilder")) {
                    // Check if it has exactly one argument (the Environment parameter)
                    if (n.getArguments().size() == 1) {
                        Expression argument = n.getArguments().get(0);
                        
                        // Create new expression: new JerseyClientBuilder().using(env)
                        ObjectCreationExpr newBuilder = new ObjectCreationExpr();
                        newBuilder.setType(n.getType());
                        newBuilder.setArguments(NodeList.nodeList());
                        
                        // Create the using() method call
                        MethodCallExpr usingCall = new MethodCallExpr();
                        usingCall.setScope(newBuilder);
                        usingCall.setName("using");
                        usingCall.setArguments(NodeList.nodeList(argument));
                        
                        // Return the new expression to replace the old one
                        return usingCall;
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}