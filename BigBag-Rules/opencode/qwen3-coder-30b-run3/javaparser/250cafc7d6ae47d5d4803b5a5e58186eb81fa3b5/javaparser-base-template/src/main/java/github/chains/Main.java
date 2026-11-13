package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDir = args[0];
        Path dirPath = Paths.get(sourceDir);
        
        if (!Files.exists(dirPath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(dirPath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformation to fix Flyway constructor calls
            FlywayConstructorFixer transformer = new FlywayConstructorFixer();
            transformer.visit(cu, null);
            
            // Save the modified file
            try (var writer = Files.newBufferedWriter(filePath)) {
                writer.write(new DefaultPrettyPrinter().print(cu));
            }
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
/**
 * Visitor that transforms Flyway no-argument constructor calls to use proper Configuration
 */
private static class FlywayConstructorFixer extends VoidVisitorAdapter<Void> {
    @Override
    public void visit(ObjectCreationExpr n, Void arg) {
        super.visit(n, arg);
        
        // Check if this is a Flyway object creation with no arguments
        if (n.getType() instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
            
            // Handle both cases: Flyway and org.flywaydb.core.Flyway
            boolean isFlyway = "Flyway".equals(type.getName().asString());
            boolean isQualifiedFlyway = false;
            
            if (type.getScope().isPresent()) {
                String scope = type.getScope().get().toString();
                if ("org.flywaydb.core".equals(scope)) {
                    isQualifiedFlyway = true;
                }
            }
            
            if (isFlyway || isQualifiedFlyway) {
                // If no arguments, this needs to be replaced with proper constructor
                if (n.getArguments().isEmpty()) {
                    System.out.println("Replacing no-arg Flyway constructor");
                    // Replace with new Flyway(Configuration) call using configure()
                    NodeList<Expression> arguments = new NodeList<>();
                    
                    // Create the call to Flyway.configure()
                    MethodCallExpr configureCall = new MethodCallExpr(
                        new NameExpr("org.flywaydb.core.Flyway"),
                        "configure"
                    );
                    
                    arguments.add(configureCall);
                    
                    // Replace the expression with new constructor call
                    ObjectCreationExpr newExpr = new ObjectCreationExpr();
                    newExpr.setType(type);
                    newExpr.setArguments(arguments);
                    
                    // Replace the whole expression
                    n.replace(newExpr);
                }
            }
        }
    }
}
}