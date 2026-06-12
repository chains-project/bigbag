package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Main {
    // Map of common numeric constant names to their values
    private static final Map<String, String> CONSTANT_VALUES = new HashMap<>();
    
    static {
        // Basic numbers
        CONSTANT_VALUES.put("ZERO", "0");
        CONSTANT_VALUES.put("ONE", "1");
        CONSTANT_VALUES.put("TWO", "2");
        CONSTANT_VALUES.put("THREE", "3");
        CONSTANT_VALUES.put("FOUR", "4");
        CONSTANT_VALUES.put("FIVE", "5");
        CONSTANT_VALUES.put("SIX", "6");
        CONSTANT_VALUES.put("SEVEN", "7");
        CONSTANT_VALUES.put("EIGHT", "8");
        CONSTANT_VALUES.put("NINE", "9");
        CONSTANT_VALUES.put("TEN", "10");
        // Common multiples
        CONSTANT_VALUES.put("TWENTY", "20");
        CONSTANT_VALUES.put("THIRTY", "30");
        CONSTANT_VALUES.put("FORTY", "40");
        CONSTANT_VALUES.put("FIFTY", "50");
        CONSTANT_VALUES.put("SIXTY", "60");
        CONSTANT_VALUES.put("SEVENTY", "70");
        CONSTANT_VALUES.put("EIGHTY", "80");
        CONSTANT_VALUES.put("NINETY", "90");
        CONSTANT_VALUES.put("HUNDRED", "100");
        CONSTANT_VALUES.put("THOUSAND", "1000");
        CONSTANT_VALUES.put("MILLION", "1000000");
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory> <fully-qualified-class-to-remove>");
            System.err.println("Example: java -jar javaparser.jar /path/to/src com.jcabi.aspects.Tv");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String classToRemove = args[1];
        String simpleClassName = classToRemove.substring(classToRemove.lastIndexOf('.') + 1);
        
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Removing references to class: " + classToRemove);
        System.out.println("Simple class name: " + simpleClassName);
        
        JavaParser parser = new JavaParser();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> transformFile(path, parser, classToRemove, simpleClassName));
        
        System.out.println("Transformation complete!");
    }
    
    private static void transformFile(Path path, JavaParser parser, String classToRemove, String simpleClassName) {
        try {
            CompilationUnit cu = parser.parse(path).getResult().orElseThrow();
            boolean modified = false;
            
            // Remove import of the class
            java.util.Iterator<ImportDeclaration> importIter = cu.getImports().iterator();
            while (importIter.hasNext()) {
                ImportDeclaration importDecl = importIter.next();
                if (importDecl.getNameAsString().equals(classToRemove)) {
                    importIter.remove();
                    modified = true;
                    System.out.println("Removed import: " + classToRemove + " from " + path);
                }
            }
            
            // Create and apply visitor to replace field accesses
            FieldAccessReplacer visitor = new FieldAccessReplacer(simpleClassName);
            visitor.visit(cu, null);
            if (visitor.wasModified()) {
                modified = true;
            }
            
            // Write back if modified
            if (modified) {
                Files.write(path, cu.toString().getBytes());
                System.out.println("Updated file: " + path);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + path + ": " + e.getMessage());
        }
    }
    
    private static class FieldAccessReplacer extends ModifierVisitor<Void> {
        private final String className;
        private boolean modified = false;
        
        FieldAccessReplacer(String className) {
            this.className = className;
        }
        
        boolean wasModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            // Check if this is a field access like Tv.TEN
            if (n.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) n.getScope();
                if (scope.getNameAsString().equals(className)) {
                    String fieldName = n.getNameAsString();
                    String replacement = CONSTANT_VALUES.get(fieldName);
                    
                    if (replacement != null) {
                        // Replace with integer literal
                        modified = true;
                        System.out.println("Replacing " + className + "." + fieldName + " with " + replacement);
                        return new IntegerLiteralExpr(replacement);
                    } else {
                        // Unknown constant - replace with 0 as fallback
                        System.err.println("Warning: Unknown constant " + className + "." + fieldName + " - replacing with 0");
                        modified = true;
                        return new IntegerLiteralExpr("0");
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}