package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar <jar-file> <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(javaFile.toFile());
                    new LoggingEventAwareFixer().visit(cu, null);
                    // Write back to file
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(cu.toString());
                    }
                } catch (Exception e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static class LoggingEventAwareFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(CompilationUnit cu, Void arg) {
            // Remove all imports of LoggingEventAware
            cu.getImports().removeIf(importDecl -> 
                importDecl.getNameAsString().contains("org.slf4j.spi.LoggingEventAware"));
            
            super.visit(cu, arg);
        }
        
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            // Remove any inheritance from LoggingEventAware
            classDecl.getExtendedTypes().removeIf(type -> 
                type.getNameAsString().contains("LoggingEventAware"));
            
            classDecl.getImplementedTypes().removeIf(type -> 
                type.getNameAsString().contains("LoggingEventAware"));
            
            super.visit(classDecl, arg);
        }
        
        @Override
        public void visit(MethodDeclaration methodDecl, Void arg) {
            // Remove any parameter types referencing LoggingEventAware
            methodDecl.getParameters().removeIf(param -> 
                param.getType().asString().contains("LoggingEventAware"));
            super.visit(methodDecl, arg);
        }
        
        @Override
        public void visit(ExpressionStmt stmt, Void arg) {
            // Look for expressions that reference LoggingEventAware
            stmt.getExpression().accept(new LoggingEventAwareExpressionVisitor(), arg);
            super.visit(stmt, arg);
        }
    }
    
    private static class LoggingEventAwareExpressionVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodCallExpr methodCall, Void arg) {
            // Check if this is a call to a method that references LoggingEventAware
            if (methodCall.getScope().isPresent()) {
                Expression scope = methodCall.getScope().get();
                if (scope instanceof NameExpr) {
                    String scopeName = ((NameExpr) scope).getNameAsString();
                    if (scopeName.contains("LoggingEventAware")) {
                        // This would be a more complex fix for actual usage
                    }
                }
            }
            super.visit(methodCall, arg);
        }
    }
}