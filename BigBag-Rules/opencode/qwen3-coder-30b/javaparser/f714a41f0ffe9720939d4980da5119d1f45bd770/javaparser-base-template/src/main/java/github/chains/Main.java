package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
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
            
            // Apply transformation to fix PNConfiguration constructor calls
            new PNConfigurationFixer().visit(cu, null);
            
            // Save the modified file
            cu.save(filePath.toFile());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that finds and fixes PNConfiguration constructor calls
     */
    private static class PNConfigurationFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ConstructorCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a call to PNConfiguration constructor with no arguments
            if (n.getType() instanceof ClassExpr) {
                ClassExpr classExpr = (ClassExpr) n.getType();
                if (classExpr.getType().asString().equals("com.pubnub.api.PNConfiguration")) {
                    // This is a call to new PNConfiguration() - we need to fix it
                    if (n.getArguments().isEmpty()) {
                        // Replace with new PNConfiguration(parameter) - this is a generic fix
                        // The actual parameter should be determined by the context
                        n.setArguments(new NodeList<>(new NameExpr("parameter")));
                    }
                }
            }
        }
    }
}