package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
                
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
            
            XEnchantmentTransformer transformer = new XEnchantmentTransformer();
            cu.accept(transformer, null);
            
            Files.write(filePath, cu.toString().getBytes());
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class XEnchantmentTransformer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Look for method calls to parseEnchantment()
            if (n.getNameAsString().equals("parseEnchantment")) {
                // Check if this is called on an XEnchantment expression
                // We need to replace parseEnchantment() with getEnchant()
                n.setName("getEnchant");
                System.out.println("  -> Replaced parseEnchantment() with getEnchant()");
            }
            
            return super.visit(n, arg);
        }
    }
}