package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Process all Java files in the project
        try {
            String sourcePath = args.length > 0 ? args[0] : "/workspace/jclouds";
            Path basePath = Paths.get(sourcePath);
            
            Files.walk(basePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix SnakeYAML constructor calls
            SnakeYamlFixVisitor visitor = new SnakeYamlFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class SnakeYamlFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this creates a Yaml object with Constructor
            if (n.getType().getNameAsString().equals("Yaml") && 
                n.getArguments().size() == 1) {
                
                Expression arg0 = n.getArguments().get(0);
                
                // If the argument is a Constructor call with a single Class argument
                if (arg0 instanceof ObjectCreationExpr) {
                    ObjectCreationExpr constructorCall = (ObjectCreationExpr) arg0;
                    
                    if (constructorCall.getType().getNameAsString().equals("Constructor") &&
                        constructorCall.getArguments().size() == 1 &&
                        constructorCall.getArguments().get(0) instanceof NameExpr) {
                        
                        // Create new Yaml constructor call with LoaderOptions
                        NodeList<Expression> newYamlArgs = new NodeList<>();
                        
                        // Add the Constructor with LoaderOptions
                        NodeList<Expression> constructorArgs = new NodeList<>();
                        constructorArgs.add(constructorCall.getArguments().get(0)); // The Class
                        
                        // Create LoaderOptions instance
                        ObjectCreationExpr loaderOptionsExpr = new ObjectCreationExpr(
                            null,
                            new ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"),
                            NodeList.nodeList()
                        );
                        
                        constructorArgs.add(loaderOptionsExpr); // Add LoaderOptions
                        
                        ObjectCreationExpr newConstructorCall = new ObjectCreationExpr(
                            constructorCall.getScope().orElse(null),
                            constructorCall.getType(),
                            constructorArgs
                        );
                        
                        newYamlArgs.add(newConstructorCall);
                        
                        // Add LoaderOptions as second argument to Yaml constructor
                        newYamlArgs.add(loaderOptionsExpr);
                        
                        // Create new Yaml constructor call
                        ObjectCreationExpr newYamlCall = new ObjectCreationExpr(
                            n.getScope().orElse(null),
                            n.getType(),
                            newYamlArgs
                        );
                        
                        return newYamlCall;
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}
