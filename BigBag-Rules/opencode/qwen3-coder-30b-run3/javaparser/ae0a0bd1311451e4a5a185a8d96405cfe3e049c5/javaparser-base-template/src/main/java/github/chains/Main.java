package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        // Usage: java -cp target/classes github.chains.Main /path/to/source/directory
        if (args.length != 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path basePath = Paths.get(sourceDir);
        
        try {
            // Process all Java files in the directory
            Files.walk(basePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply the transformation
            HamcrestFixVisitor visitor = new HamcrestFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(cu.toString().getBytes());
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that fixes Hamcrest API calls with wrong constructor signatures
     */
    private static class HamcrestFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a StringContains or StringStartsWith constructor call
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                String typeName = type.getNameAsString();
                
                if ("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) {
                    // Get all arguments
                    com.github.javaparser.ast.NodeList<Expression> args = n.getArguments();
                    
                    // If we have exactly 2 arguments and the first is a boolean literal,
                    // we need to remove the boolean argument
                    if (args.size() == 2 && args.get(0) instanceof BooleanLiteralExpr) {
                        // Remove the first argument (boolean) and keep only the second (String)
                        Expression newArg = args.get(1);
                        
                        // Create a new constructor call with just the string argument
                        ObjectCreationExpr newExpr = new ObjectCreationExpr();
                        newExpr.setType(type);
                        newExpr.setArguments(new com.github.javaparser.ast.NodeList<>());
                        newExpr.getArguments().add(newArg);
                        
                        return newExpr;
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}