package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.file.*;
import java.io.*;

public class Main {
    
    public static void main(String[] args) {
        try {
            // Process all Java files in the source directory
            String sourceDir = args.length > 0 ? args[0] : "/workspace/billy";
            processDirectory(Paths.get(sourceDir));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = new String(Files.readAllBytes(filePath));
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformations
            TransformVisitor visitor = new TransformVisitor();
            visitor.visit(cu, null);
            
            // Write back if changes were made
            if (visitor.hasChanged()) {
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class TransformVisitor extends VoidVisitorAdapter<Void> {
        private boolean changed = false;
        
        public boolean hasChanged() {
            return changed;
        }
        
        @Override
        public void visit(ConstructorCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Handle Yaml constructor calls - look for the specific pattern that's broken
            if (n.getNameAsString().equals("Yaml")) {
                handleYamlConstructor(n);
            }
        }
        
        private void handleYamlConstructor(ConstructorCallExpr n) {
            // Transform SnakeYAML 2.1 breaking changes in Yaml constructors
            NodeList<Expression> args = n.getArguments();
            
            // Pattern: new Yaml(constructor, representer) 
            // Should be transformed to: new Yaml(constructor, representer, new DumperOptions(), new LoaderOptions())
            if (args.size() == 2) {
                // Add DumperOptions and LoaderOptions parameters
                args.add(new ObjectCreationExpr(null, 
                    new Name("DumperOptions"), 
                    NodeList.nodeList()));
                
                args.add(new ObjectCreationExpr(null, 
                    new Name("LoaderOptions"), 
                    NodeList.nodeList()));
                
                changed = true;
            }
            // Pattern: new Yaml(constructor, representer, dumperOptions)
            // Should be transformed to: new Yaml(constructor, representer, dumperOptions, new LoaderOptions())
            else if (args.size() == 3) {
                // Check if third argument is DumperOptions
                if (args.get(2) instanceof ObjectCreationExpr) {
                    ObjectCreationExpr expr = (ObjectCreationExpr) args.get(2);
                    if (expr.getType().toString().endsWith("DumperOptions")) {
                        // Add LoaderOptions as fourth parameter
                        args.add(new ObjectCreationExpr(null, 
                            new Name("LoaderOptions"), 
                            NodeList.nodeList()));
                        changed = true;
                    }
                }
            }
        }
    }
}
