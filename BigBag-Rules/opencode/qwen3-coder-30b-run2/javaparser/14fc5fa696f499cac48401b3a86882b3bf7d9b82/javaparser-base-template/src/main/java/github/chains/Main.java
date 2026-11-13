package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileWriter;

public class Main {
    
    public static void main(String[] args) throws Exception {
        // Usage: java Main /path/to/source/code
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            new FlywayTransformationVisitor().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                LexicalPreservingPrinter.print(cu, writer);
            }
        } catch (FileNotFoundException e) {
            System.err.println("Could not parse file: " + filePath);
        }
    }
    
    /**
     * Visitor to transform Flyway API usage from old style to new style
     */
    private static class FlywayTransformationVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            
            // Look for method bodies that contain Flyway instantiation and configuration
            md.getBody().ifPresent(body -> {
                NodeList<Statement> statements = body.getStatements();
                
                // First pass: Handle new Flyway() constructor call
                for (int i = 0; i < statements.size(); i++) {
                    Statement stmt = statements.get(i);
                    
                    // Handle object creation: new Flyway() -> new Flyway(FluentConfiguration.configure())
                    if (stmt instanceof ExpressionStmt) {
                        ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                        Expression expr = exprStmt.getExpression();
                        
                        if (expr instanceof ObjectCreationExpr) {
                            ObjectCreationExpr creation = (ObjectCreationExpr) expr;
                            
                            // Handle: new Flyway()
                            if (creation.getType().getNameAsString().equals("Flyway")) {
                                NodeList<Expression> args = creation.getArguments();
                                
                                if (args.isEmpty()) {
                                    // Transform from: new Flyway() to new Flyway(FluentConfiguration.configure())
                                    Expression newExpr = new ObjectCreationExpr(
                                        null,
                                        new ClassOrInterfaceType(null, "org.flywaydb.core.Flyway"),
                                        NodeList.nodeList(
                                            new MethodCallExpr(
                                                new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                                                "configure"
                                            )
                                        )
                                    );
                                    exprStmt.setExpression(newExpr);
                                }
                            }
                        }
                    }
                }
            });
        }
    }
}