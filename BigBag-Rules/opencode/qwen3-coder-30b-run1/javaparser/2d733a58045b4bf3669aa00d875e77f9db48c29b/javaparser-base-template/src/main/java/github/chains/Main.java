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
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.Expression;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java Main <source-file-path>");
            return;
        }

        String sourceFilePath = args[0];
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(sourceFilePath));
            
            // Apply the Flyway migration transformation
            new FlywayMigrationTransformer().visit(cu, null);
            
            // Save the modified file
            try (FileWriter writer = new FileWriter(sourceFilePath)) {
                writer.write(cu.toString());
            }
            
            System.out.println("Transformation completed successfully on " + sourceFilePath);
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Transformer that converts old Flyway API calls to new API calls
     */
    private static class FlywayMigrationTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is creating a Flyway instance with no arguments
            if (n.getType().getNameAsString().equals("Flyway")) {
                // Check if it's a no-arg constructor call
                if (n.getArguments().isEmpty()) {
                    // Replace with FluentConfiguration approach
                    replaceFlywayNoArgConstructor(n);
                }
            }
        }
        
        private void replaceFlywayNoArgConstructor(ObjectCreationExpr n) {
            // Create new FluentConfiguration approach
            // Instead of: new Flyway()
            // Use: new Flyway(FluentConfiguration.configure())
            
            // Create the FluentConfiguration.configure() call
            MethodCallExpr configureCall = new MethodCallExpr(
                new NameExpr("org.flywaydb.core.api.configuration.FluentConfiguration"),
                "configure"
            );
            
            // Set the arguments to use the new configuration approach
            n.setArguments(NodeList.nodeList(configureCall));
            n.setType(new ClassOrInterfaceType("org.flywaydb.core.Flyway"));
        }
    }
}