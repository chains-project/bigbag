package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    private static class ParseEnchantmentToGetEnchantVisitor extends VoidVisitorAdapter<Void> {
        private final List<Path> modifiedFiles = new ArrayList<>();
        private boolean modificationsMade = false;
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a parseEnchantment() method call
            if (n.getNameAsString().equals("parseEnchantment")) {
                // In XSeries 8.6.0, parseEnchantment() was renamed to getEnchant()
                // This affects all calls to parseEnchantment() on XEnchantment objects
                n.setName("getEnchant");
                modificationsMade = true;
            }
        }
        
        public boolean hasModifications() {
            return modificationsMade;
        }
        
        public List<Path> getModifiedFiles() {
            return modifiedFiles;
        }
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming parseEnchantment() to getEnchant() in: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        
        JavaParser parser = new JavaParser();
        int totalModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Warning: Could not parse " + javaFile);
                    continue;
                }
                
                ParseEnchantmentToGetEnchantVisitor visitor = new ParseEnchantmentToGetEnchantVisitor();
                visitor.visit(cu, null);
                
                if (visitor.hasModifications()) {
                    // Write the modified file back
                    Files.write(javaFile, cu.toString().getBytes());
                    totalModified++;
                    System.out.println("Modified: " + javaFile);
                }
                
            } catch (IOException e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete. Modified " + totalModified + " files.");
    }
}