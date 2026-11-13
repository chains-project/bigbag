package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.printer.*;
import com.github.javaparser.printer.configuration.*;
import com.github.javaparser.resolution.*;
import com.github.javaparser.symbolsolver.*;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            transformDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String sourceDir) throws IOException {
        Path start = Paths.get(sourceDir);
        Files.walk(start)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            JavaParser javaParser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = javaParser.parse(filePath);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
                boolean modified = false;
                
                for (MethodCallExpr methodCall : methodCalls) {
                    // Check if this is a call to getInstance() method
                    if ("getInstance".equals(methodCall.getNameAsString())) {
                        // Check if the scope is JAXBToStringStrategy
                        if (methodCall.getScope().isPresent()) {
                            Expression scope = methodCall.getScope().get();
                            
                            // Convert scope to string and check if it ends with JAXBToStringStrategy
                            String scopeString = scope.toString();
                            if (scopeString.endsWith("JAXBToStringStrategy") || 
                                scopeString.endsWith("org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy")) {
                                // Replace method call with field access
                                // For JAXBToStringStrategy.getInstance() -> JAXBToStringStrategy.INSTANCE
                                // For fully qualified names, keep them as is
                                FieldAccessExpr fieldAccess = new FieldAccessExpr(
                                    scope,
                                    "INSTANCE"
                                );
                                methodCall.replace(fieldAccess);
                                modified = true;
                            }
                        } else {
                            // No scope - could be a static import
                            // Check imports to see if JAXBToStringStrategy is imported
                            Optional<CompilationUnit> cuOpt = methodCall.findAncestor(CompilationUnit.class);
                            if (cuOpt.isPresent()) {
                                CompilationUnit cuLocal = cuOpt.get();
                                boolean hasImport = cuLocal.getImports().stream()
                                    .anyMatch(importDecl -> 
                                        importDecl.getNameAsString().endsWith(".JAXBToStringStrategy") ||
                                        importDecl.getNameAsString().equals("org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy"));
                                
                                if (hasImport) {
                                    // Replace with JAXBToStringStrategy.INSTANCE
                                    FieldAccessExpr fieldAccess = new FieldAccessExpr(
                                        new NameExpr("JAXBToStringStrategy"),
                                        "INSTANCE"
                                    );
                                    methodCall.replace(fieldAccess);
                                    modified = true;
                                }
                            }
                        }
                    }
                }
                
                if (modified) {
                    // Save the transformed file
                    try (PrintWriter out = new PrintWriter(filePath.toFile())) {
                        out.print(cu.toString());
                    }
                    System.out.println("Transformed: " + filePath);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing file: " + filePath + " - " + e.getMessage());
        }
    }
}