package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class Main {
    
    // Map of Tv constants to their integer values
    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();
    
    static {
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("TWENTY", 20);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
        TV_CONSTANTS.put("BILLION", 1000000000);
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("FOUR", 4);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("SIX", 6);
        TV_CONSTANTS.put("FIFTEEN", 15);
        TV_CONSTANTS.put("THIRTY", 30);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing Java files in: " + sourceDir);
        
        try {
            Files.walkFileTree(sourceDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toString().endsWith(".java")) {
                            processJavaFile(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            
            System.out.println("Transformation completed successfully.");
            
        } catch (IOException e) {
            System.err.println("Error walking directory tree: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Warning: Could not parse file: " + file);
                return;
            }
            
            // Check if file imports com.jcabi.aspects.Tv
            boolean hasTvImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("com.jcabi.aspects.Tv"));
            
            // Create visitor to replace Tv constants
            TvConstantReplacer replacer = new TvConstantReplacer();
            cu.accept(replacer, null);
            
            // Remove the Tv import if it exists
            if (hasTvImport) {
                cu.getImports().removeIf(imp -> imp.getNameAsString().equals("com.jcabi.aspects.Tv"));
            }
            
            // Write back to file if changes were made
            if (replacer.changesMade || hasTvImport) {
                Files.write(file, cu.toString().getBytes());
                System.out.println("Updated: " + file);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
    
    private static class TvConstantReplacer extends ModifierVisitor<Void> {
        private boolean changesMade = false;
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is a field access expression like Tv.CONSTANT
            if (n.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) n.getScope();
                if (scope.getNameAsString().equals("Tv")) {
                    String constantName = n.getNameAsString();
                    Integer constantValue = TV_CONSTANTS.get(constantName);
                    
                    if (constantValue != null) {
                        // Replace Tv.CONSTANT with integer literal
                        changesMade = true;
                        return new IntegerLiteralExpr(String.valueOf(constantValue));
                    } else {
                        System.err.println("Warning: Unknown Tv constant: " + constantName);
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}