package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);

        // Find all .java files
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }

    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);

            // Apply transformation to fix Flyway constructor calls
            FlywayConstructorFixer visitor = new FlywayConstructorFixer();
            visitor.visit(cu, null);

            // Write back the modified content
            Files.writeString(filePath, cu.toString());
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }

    static class FlywayConstructorFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a Flyway constructor call
            if (n.getType().getNameAsString().equals("Flyway")) {
                // Check if it's a parameterless constructor (the old way)
                if (n.getArguments().isEmpty()) {
                    // Replace with proper configuration-based constructor
                    // We'll create a configuration using FluentConfiguration
                    NodeList<Expression> newArgs = new NodeList<>();
                    MethodCallExpr configureCall = new MethodCallExpr(null, "configure");
                    MethodCallExpr loadCall = new MethodCallExpr(configureCall, "load");
                    newArgs.add(loadCall);
                    n.setArguments(newArgs);
                }
            }
        }
    }
}