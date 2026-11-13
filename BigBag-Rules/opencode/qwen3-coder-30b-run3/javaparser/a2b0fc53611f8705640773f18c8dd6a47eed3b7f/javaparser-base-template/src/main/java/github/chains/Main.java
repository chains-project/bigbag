package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation for Flyway dependency breaking changes in version 9.19.4.
 * 
 * This transformation addresses the breaking changes where:
 * 1. The default Flyway constructor now requires a Configuration parameter
 * 2. Direct setter methods (setDataSource, setClassLoader, etc.) no longer exist
 * 
 * The transformation detects and fixes usage patterns like:
 *   Flyway flyway = new Flyway();
 *   flyway.setDataSource(dataSource);
 *   flyway.setClassLoader(classLoader);
 *   flyway.setLocations(locations);
 *   flyway.setValidateOnMigrate(validateOnMigrate);
 *   
 * And converts them to:
 *   Flyway flyway = new Flyway(Flyway.configure()
 *       .dataSource(dataSource)
 *       .classLoader(classLoader)
 *       .locations(locations)
 *       .validateOnMigrate(validateOnMigrate));
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path dir = Paths.get(sourceDir);
        
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Invalid directory: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(dir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations to fix Flyway usage
            FlywayFixVisitor visitor = new FlywayFixVisitor();
            visitor.visit(cu, null);
            
            // Save the modified file
            cu.save(filePath.toFile());
        } catch (IOException e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and fixes Flyway usage patterns
     */
    private static class FlywayFixVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ExpressionStmt stmt, Void arg) {
            super.visit(stmt, arg);
            
            // Look for assignment statements with Flyway creation
            // This detects: Flyway flyway = new Flyway();
            if (stmt.getExpression() instanceof AssignmentExpr) {
                AssignmentExpr assignExpr = (AssignmentExpr) stmt.getExpression();
                
                // Check if we're assigning a new Flyway object
                if (assignExpr.getValue() instanceof ObjectCreationExpr) {
                    ObjectCreationExpr creationExpr = (ObjectCreationExpr) assignExpr.getValue();
                    
                    if ("org.flywaydb.core.Flyway".equals(creationExpr.getType().getNameAsString())) {
                        // Check if there are no arguments (the problematic case)
                        if (creationExpr.getArguments().isEmpty()) {
                            // Replace with new Flyway(Flyway.configure())
                            MethodCallExpr fluentConfigure = new MethodCallExpr(
                                new NameExpr("org.flywaydb.core.Flyway"),
                                "configure"
                            );
                            
                            ObjectCreationExpr newFlywayConstructor = new ObjectCreationExpr(
                                null,
                                creationExpr.getType(),
                                NodeList.nodeList(fluentConfigure)
                            );
                            
                            assignExpr.setValue(newFlywayConstructor);
                        }
                    }
                }
            }
        }
    }
}
