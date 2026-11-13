package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        
        Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        try {
            JavaParser parser = new JavaParser();
            PrettyPrinter printer = new PrettyPrinter();
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
                    HamcrestApiVisitor visitor = new HamcrestApiVisitor();
                    cu.accept(visitor, null);
                    
                    if (visitor.isModified()) {
                        String transformed = printer.print(cu);
                        Files.write(javaFile, transformed.getBytes());
                        transformedFiles++;
                        transformedCalls += visitor.getTransformCount();
                        System.out.println("Transformed: " + javaFile);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files modified: " + transformedFiles);
        System.out.println("  API calls transformed: " + transformedCalls);
    }
    
    private static class HamcrestApiVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private int transformCount = 0;
        
        @Override
        public Visitable visit(MethodCallExpr expr, Void arg) {
            // Handle static method calls like StringContains.containsString(text)
            if (expr.getScope().isPresent()) {
                String scopeName = expr.getScope().get().toString();
                String methodName = expr.getName().asString();
                
                boolean isStringContainsScope = scopeName.equals("StringContains") 
                    || scopeName.equals("org.hamcrest.core.StringContains");
                    
                boolean isStringStartsWithScope = scopeName.equals("StringStartsWith")
                    || scopeName.equals("org.hamcrest.core.StringStartsWith");
                
                if (isStringContainsScope && expr.getArguments().size() == 1) {
                    if (methodName.equals("containsString") || methodName.equals("containsStringIgnoringCase")) {
                        // Convert to constructor call
                        com.github.javaparser.ast.type.ClassOrInterfaceType type = 
                            new com.github.javaparser.ast.type.ClassOrInterfaceType(
                                new com.github.javaparser.ast.expr.SimpleName(scopeName)
                            );
                        ObjectCreationExpr constructor = new ObjectCreationExpr(
                            null,
                            type,
                            new NodeList<>(expr.getArguments())
                        );
                        modified = true;
                        transformCount++;
                        return constructor;
                    }
                } else if (isStringStartsWithScope && expr.getArguments().size() == 1) {
                    if (methodName.equals("startsWith") || methodName.equals("startsWithIgnoringCase")) {
                        // Convert to constructor call
                        com.github.javaparser.ast.type.ClassOrInterfaceType type = 
                            new com.github.javaparser.ast.type.ClassOrInterfaceType(
                                new com.github.javaparser.ast.expr.SimpleName(scopeName)
                            );
                        ObjectCreationExpr constructor = new ObjectCreationExpr(
                            null,
                            type,
                            new NodeList<>(expr.getArguments())
                        );
                        modified = true;
                        transformCount++;
                        return constructor;
                    }
                }
            }
            
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getType().asString();
            
            boolean isStringContains = typeName.equals("StringContains") 
                || typeName.equals("org.hamcrest.core.StringContains")
                || typeName.endsWith(".StringContains");
                
            boolean isStringStartsWith = typeName.equals("StringStartsWith")
                || typeName.equals("org.hamcrest.core.StringStartsWith")
                || typeName.endsWith(".StringStartsWith");
            
            if ((isStringContains || isStringStartsWith) && expr.getArguments().size() == 2) {
                // Two-argument constructor - remove boolean parameter
                if (expr.getArgument(0).isBooleanLiteralExpr()) {
                    ObjectCreationExpr transformed = expr.clone();
                    transformed.getArguments().remove(0);
                    modified = true;
                    transformCount++;
                    return transformed;
                }
            }
            
            return super.visit(expr, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
        
        public int getTransformCount() {
            return transformCount;
        }
    }
}
