package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        // Find all Java files and process them
        Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Apply transformation to fix the generics issue
            TransformationVisitor visitor = new TransformationVisitor();
            cu.accept(visitor, null);
            
            // Write back the modified content
            Files.writeString(filePath, cu.toString());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    static class TransformationVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Look for variable declarations with getRegistry() assignment
            for (VariableDeclarator var : n.getVariables()) {
                if (var.getInitializer().isPresent() && var.getInitializer().get() instanceof MethodCallExpr) {
                    MethodCallExpr methodCall = (MethodCallExpr) var.getInitializer().get();
                    if (methodCall.getNameAsString().equals("getRegistry") && methodCall.getScope().isPresent()) {
                        // We found a getRegistry() call - now check if it's assigned to a SortedMap
                        Type type = n.getElementType();
                        if (type instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
                            if (classType.getNameAsString().equals("SortedMap")) {
                                // This matches the problematic pattern: SortedMap<MOScope, ManagedObject>
                                // We want to identify where this pattern occurs
                                // For now we just note that we found it
                                System.out.println("Found getRegistry() assignment pattern: " + n.toString());
                            }
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}