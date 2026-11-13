package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully processed " + modifiedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        JavaParser parser = new JavaParser();
        ParseResult<CompilationUnit> parseResult = parser.parse(content);
        
        if (!parseResult.isSuccessful()) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        CompilationUnit cu = parseResult.getResult().get();
        FlywayConstructorTransformer transformer = new FlywayConstructorTransformer();
        CompilationUnit modifiedCu = (CompilationUnit) cu.accept(transformer, null);
        
        if (transformer.wasModified()) {
            String modifiedContent = modifiedCu.toString();
            Files.writeString(filePath, modifiedContent);
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    private static class FlywayConstructorTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a new Flyway() constructor call
            if (n.getType().asString().equals("Flyway") && 
                n.getArguments().isEmpty()) {
                
                // Replace with new Flyway(Flyway.configure().load())
                MethodCallExpr loadCall = new MethodCallExpr(
                    new MethodCallExpr(
                        new NameExpr("Flyway"), "configure"
                    ), "load"
                );
                
                n.getArguments().add(loadCall);
                modified = true;
            }
            
            return super.visit(n, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}