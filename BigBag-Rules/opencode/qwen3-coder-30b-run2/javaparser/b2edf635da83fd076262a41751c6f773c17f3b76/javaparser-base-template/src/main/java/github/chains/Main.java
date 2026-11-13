package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the target directory
        String sourceDir = args.length > 0 ? args[0] : "/workspace/jclouds";
        
        // Find all Java files
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        // Process each file
        int processedCount = 0;
        for (Path file : javaFiles) {
            if (processFile(file)) {
                processedCount++;
            }
        }
        
        System.out.println("Processed " + processedCount + " Java files with changes.");
    }
    
    private static boolean processFile(Path file) throws IOException {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file.toFile());
            
            // Apply the SnakeYAML transformation
            SnakeYAMLTransformer transformer = new SnakeYAMLTransformer();
            transformer.visit(cu, null);
            
            // Save the modified file if changes were made
            String originalContent = Files.readString(file);
            String newContent = cu.toString();
            
            if (!originalContent.equals(newContent)) {
                Files.writeString(file, newContent);
                System.out.println("Modified: " + file);
                return true;
            } else {
                // System.out.println("No changes: " + file);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
            return false;
        }
    }
    
    private static class SnakeYAMLTransformer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr objectCreationExpr, Void arg) {
            super.visit(objectCreationExpr, arg);
            
            // Look for new Yaml(...) calls that have a Constructor as first argument
            if (objectCreationExpr.getType().asString().equals("Yaml")) {
                NodeList<Expression> arguments = objectCreationExpr.getArguments();
                
                if (!arguments.isEmpty() && arguments.get(0) instanceof ObjectCreationExpr) {
                    ObjectCreationExpr firstArg = (ObjectCreationExpr) arguments.get(0);
                    
                    if (firstArg.getType().asString().equals("Constructor")) {
                        // This pattern needs to be transformed to Yaml(Constructor, LoaderOptions)
                        
                        // Create new arguments list
                        NodeList<Expression> newArgs = new NodeList<>();
                        
                        // Add existing Constructor argument
                        newArgs.add(arguments.get(0));
                        
                        // Add new LoaderOptions argument
                        ObjectCreationExpr loaderOptionsCreation = new ObjectCreationExpr();
                        loaderOptionsCreation.setType(new ClassOrInterfaceType("org.yaml.snakeyaml.LoaderOptions"));
                        newArgs.add(loaderOptionsCreation);
                        
                        // Replace the whole expression
                        objectCreationExpr.setArguments(newArgs);
                    }
                }
            }
        }
    }
}
