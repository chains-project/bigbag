package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the project
        String projectPath = args.length > 0 ? args[0] : "/workspace/simplelocalize-cli";
        System.out.println("Processing project at: " + projectPath);
        
        // Find all Java files recursively
        Files.walk(Paths.get(projectPath))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix SnakeYAML 2.0 API changes
            SnakeYAMLTransformer transformer = new SnakeYAMLTransformer();
            transformer.visit(cu, null);
            
            // Save the modified file using the correct method
            cu.toString(); // This forces the AST to be serialized to string
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(cu.toString().getBytes());
            }
            
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Transformer for fixing SnakeYAML 2.0 API incompatibilities
     */
    private static class SnakeYAMLTransformer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr objectCreation, Void arg) {
            super.visit(objectCreation, arg);
            
            // Check if this is a Constructor creation that needs to be updated for SnakeYAML 2.0
            if (objectCreation.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) objectCreation.getType();
                if ("Constructor".equals(type.getNameAsString())) {
                    // Handle the Constructor class creation pattern that changed in SnakeYAML 2.0
                    // Old: new Constructor(Configuration.class) 
                    // New: new Constructor(Configuration.class, new LoaderOptions())
                    handleConstructorCreation(objectCreation);
                }
            }
        }
        
        private void handleConstructorCreation(ObjectCreationExpr constructorCall) {
            NodeList<Expression> args = constructorCall.getArguments();
            
            // If it's a Constructor call with just one argument (the class), we need to add LoaderOptions
            if (args.size() == 1 && args.get(0) instanceof ClassExpr) {
                System.out.println("Found Constructor creation with single class argument - need to add LoaderOptions");
                
                // Create new arguments list with LoaderOptions
                NodeList<Expression> newArgs = new NodeList<>();
                newArgs.add(args.get(0)); // Original class argument
                
                // Add new LoaderOptions() as second argument
                ObjectCreationExpr loaderOptionsCreation = new ObjectCreationExpr();
                loaderOptionsCreation.setType("org.yaml.snakeyaml.LoaderOptions");
                loaderOptionsCreation.setArguments(new NodeList<>());
                
                newArgs.add(loaderOptionsCreation);
                
                // Update the constructor call with the new arguments
                constructorCall.setArguments(newArgs);
            }
        }
    }
}
