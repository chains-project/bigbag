package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration: List of class-field pairs that need transformation
    // Format: "fully.qualified.ClassName.fieldName"
    private static final List<String> FIELD_REPLACEMENTS = new ArrayList<>();
    
    static {
        // Add the specific GHCompare.status -> GHCompare.getStatus() transformation
        FIELD_REPLACEMENTS.add("org.kohsuke.github.GHCompare.status");
        // Add more field replacements here as needed for other breaking changes
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int totalReplacements = 0;
            for (Path javaFile : javaFiles) {
                int replacements = processFile(javaFile);
                if (replacements > 0) {
                    System.out.println("  " + javaFile + ": " + replacements + " replacement(s)");
                    totalReplacements += replacements;
                }
            }
            
            System.out.println("\nTotal replacements made: " + totalReplacements);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        return Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static int processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new IOException("Failed to parse " + javaFile)
        );
        
        FieldAccessTransformer transformer = new FieldAccessTransformer();
        cu.accept(transformer, null);
        
        if (transformer.getReplacementsCount() > 0) {
            Files.write(javaFile, cu.toString().getBytes());
        }
        
        return transformer.getReplacementsCount();
    }
    
    private static class FieldAccessTransformer extends ModifierVisitor<Void> {
        private int replacementsCount = 0;
        
        public int getReplacementsCount() {
            return replacementsCount;
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Get the field name being accessed
            String fieldName = n.getNameAsString();
            
            // Check if this field access matches any of our replacement patterns
            for (String pattern : FIELD_REPLACEMENTS) {
                String[] parts = pattern.split("\\.");
                if (parts.length < 2) continue;
                
                String className = String.join(".", java.util.Arrays.copyOf(parts, parts.length - 1));
                String targetFieldName = parts[parts.length - 1];
                
                // For simplicity, we'll transform any field access with matching field name
                // In a more advanced implementation, we would use type resolution to check
                // if the expression has the correct type
                if (fieldName.equals(targetFieldName)) {
                    // Replace field access with method call
                    String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                    
                    MethodCallExpr methodCall = new MethodCallExpr(n.getScope(), getterName);
                    replacementsCount++;
                    
                    return methodCall;
                }
            }
            
            return super.visit(n, arg);
        }
    }
}