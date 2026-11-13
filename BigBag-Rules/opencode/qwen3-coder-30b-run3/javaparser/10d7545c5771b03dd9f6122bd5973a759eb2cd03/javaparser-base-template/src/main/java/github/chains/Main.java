package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix Dropwizard client breaking changes.
 * Specifically addresses the DropwizardApacheConnector constructor signature change
 * from version 3.x to 4.0.0 where a boolean parameter was added.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        
        // Process all Java files in the directory
        Path dir = Paths.get(sourceDir);
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix Dropwizard Apache Connector constructor calls
            DropwizardClientFixVisitor visitor = new DropwizardClientFixVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            cu.toString(); // This outputs to string, but we need to save it properly
            
            // Write back to file
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that transforms Dropwizard Apache Connector constructor calls
     * to match the new API signature in version 4.0.0
     */
    private static class DropwizardClientFixVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Look for DropwizardApacheConnector constructor calls
            if (n.getType().toString().equals("DropwizardApacheConnector")) {
                
                // If we have 2 arguments (old API), we need to add a third boolean argument
                if (n.getArguments().size() == 2) {
                    // Add a third argument: true (for the new boolean parameter)
                    NodeList<Expression> newArgs = new NodeList<>();
                    newArgs.addAll(n.getArguments());
                    newArgs.add(new BooleanLiteralExpr(true));
                    
                    return n.setArguments(newArgs);
                }
            }
            return super.visit(n, arg);
        }
    }
}
