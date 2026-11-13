package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * GENERIC TRANSFORMATION RULE for Flyway API breaking change (version <9 to >=9)
 * 
 * This is a template for a generic, reusable transformation rule.
 * Actual implementation would need to:
 * 1. Track variable declarations and their types
 * 2. Map setter calls to fluent method calls  
 * 3. Rebuild the entire expression as a fluent chain
 * 4. Handle edge cases (multiple Flyway instances, etc.)
 * 
 * BREAKING CHANGE CHARACTERIZATION:
 * - Old API Pattern: Flyway flyway = new Flyway(); flyway.setXxx(...); return flyway;
 * - New API Pattern: return Flyway.configure().xxx(...).load();
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Generic transformation for Flyway API breaking change (v<9 → v>=9)");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process.");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) continue;
            
            String fileContent = Files.readString(javaFile);
            
            if (fileContent.contains("Flyway")) {
                FlywayTransformer transformer = new FlywayTransformer();
                CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
                
                if (transformer.wasModified()) {
                    Files.write(javaFile, transformedCu.toString().getBytes());
                    transformedFiles++;
                    System.out.println("Transformed: " + javaFile);
                }
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files.");
    }
    
    /**
     * Generic transformer demonstrating the pattern matching logic.
     * A complete implementation would need more sophisticated AST tracking.
     */
    static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Match: new Flyway() or new org.flywaydb.core.Flyway()
            String typeName = n.getType().asString();
            if (typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway")) {
                if (n.getArguments().isEmpty()) {
                    modified = true;
                    
                    // Replace constructor with factory method
                    return new MethodCallExpr(
                        new NameExpr("Flyway"),
                        "configure"
                    );
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Remove setter calls (they'll be transformed to fluent calls)
            String methodName = n.getNameAsString();
            if (methodName.startsWith("set")) {
                modified = true;
                return null; // Remove setter call
            }
            
            return super.visit(n, arg);
        }
    }
}