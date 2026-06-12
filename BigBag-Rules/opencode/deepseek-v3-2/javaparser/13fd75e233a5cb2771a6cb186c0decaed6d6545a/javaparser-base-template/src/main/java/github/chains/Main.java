package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        int transformed = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                // Create a custom visitor that tracks modifications
                ModificationTrackingVisitor visitor = new ModificationTrackingVisitor();
                cu.accept(visitor, null);
                
                if (visitor.isModified()) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformed++;
                    System.out.println("Transformed: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformed + " files");
    }
    
    private static class ModificationTrackingVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            String typeName = expr.getType().asString();
            
            // Handle StringContains and StringStartsWith: transform 2-arg (boolean, String) to 1-arg (String)
            // This converts from new API to old API
            if (typeName.equals("StringContains") || typeName.equals("StringStartsWith")) {
                if (expr.getArguments().size() == 2) {
                    // Check if first argument is a boolean literal
                    if (expr.getArgument(0).isBooleanLiteralExpr()) {
                        // Remove the boolean argument, keep only the string argument
                        expr.getArguments().remove(0);
                        modified = true;
                        return expr;
                    }
                }
            }
            
            // Handle AllOf: check if it's using the new API AllOf<>(Iterable) 
            // and convert to old API if needed
            // Note: This is a more complex transformation that might require
            // analyzing the argument type
            if (typeName.equals("AllOf")) {
                // Check if it has exactly 1 argument (Iterable)
                if (expr.getArguments().size() == 1) {
                    // For now, we'll mark it as modified but not change it
                    // In a real implementation, we would need to check if this
                    // is the new API signature and convert to old API
                    // modified = true;
                }
            }
            
            return super.visit(expr, arg);
        }
    }
}