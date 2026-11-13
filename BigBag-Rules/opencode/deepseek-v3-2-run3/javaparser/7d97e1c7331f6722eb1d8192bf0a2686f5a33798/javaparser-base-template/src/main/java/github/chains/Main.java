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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Main {
    // Map of Tv constant names to their integer values
    private static final Map<String, Integer> TV_CONSTANTS = new HashMap<>();
    
    static {
        // Common numeric constants based on naming patterns
        TV_CONSTANTS.put("ONE", 1);
        TV_CONSTANTS.put("TWO", 2);
        TV_CONSTANTS.put("THREE", 3);
        TV_CONSTANTS.put("FOUR", 4);
        TV_CONSTANTS.put("FIVE", 5);
        TV_CONSTANTS.put("SIX", 6);
        TV_CONSTANTS.put("SEVEN", 7);
        TV_CONSTANTS.put("EIGHT", 8);
        TV_CONSTANTS.put("NINE", 9);
        TV_CONSTANTS.put("TEN", 10);
        TV_CONSTANTS.put("ELEVEN", 11);
        TV_CONSTANTS.put("TWELVE", 12);
        TV_CONSTANTS.put("THIRTEEN", 13);
        TV_CONSTANTS.put("FOURTEEN", 14);
        TV_CONSTANTS.put("FIFTEEN", 15);
        TV_CONSTANTS.put("SIXTEEN", 16);
        TV_CONSTANTS.put("SEVENTEEN", 17);
        TV_CONSTANTS.put("EIGHTEEN", 18);
        TV_CONSTANTS.put("NINETEEN", 19);
        TV_CONSTANTS.put("TWENTY", 20);
        TV_CONSTANTS.put("THIRTY", 30);
        TV_CONSTANTS.put("FORTY", 40);
        TV_CONSTANTS.put("FIFTY", 50);
        TV_CONSTANTS.put("SIXTY", 60);
        TV_CONSTANTS.put("SEVENTY", 70);
        TV_CONSTANTS.put("EIGHTY", 80);
        TV_CONSTANTS.put("NINETY", 90);
        TV_CONSTANTS.put("HUNDRED", 100);
        TV_CONSTANTS.put("THOUSAND", 1000);
        TV_CONSTANTS.put("MILLION", 1000000);
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws FileNotFoundException {
        File dir = directory.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Directory does not exist: " + directory);
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            processJavaFile(file);
        }
        
        // Recursively process subdirectories
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                processDirectory(subdir.toPath());
            }
        }
    }
    
    private static void processJavaFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> optionalCu = parser.parse(javaFile).getResult();
        
        if (!optionalCu.isPresent()) {
            System.err.println("Failed to parse: " + javaFile);
            return;
        }
        
        CompilationUnit cu = optionalCu.get();
        TvTransformer transformer = new TvTransformer();
        CompilationUnit transformed = (CompilationUnit) cu.accept(transformer, null);
        
        // Write back transformed file
        try {
            FileOutputStream fos = new FileOutputStream(javaFile);
            fos.write(transformed.toString().getBytes());
            fos.close();
            System.out.println("Transformed: " + javaFile);
        } catch (IOException e) {
            System.err.println("Failed to write transformed file: " + javaFile + " - " + e.getMessage());
        }
    }
    
    private static class TvTransformer extends ModifierVisitor<Void> {
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            // Remove import of com.jcabi.aspects.Tv (both regular and static)
            String importName = importDecl.getNameAsString();
            if (importName.equals("com.jcabi.aspects.Tv") || 
                (importDecl.isStatic() && importName.startsWith("com.jcabi.aspects.Tv"))) {
                return null; // Remove the import
            }
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(FieldAccessExpr fieldAccess, Void arg) {
            // Check if this is a Tv.CONSTANT expression
            if (fieldAccess.getScope() instanceof NameExpr) {
                NameExpr scope = (NameExpr) fieldAccess.getScope();
                if (scope.getNameAsString().equals("Tv")) {
                    String constantName = fieldAccess.getNameAsString();
                    
                    // Check if this is a known Tv constant
                    if (TV_CONSTANTS.containsKey(constantName)) {
                        int value = TV_CONSTANTS.get(constantName);
                        // Replace Tv.CONSTANT with integer literal
                        return new IntegerLiteralExpr(String.valueOf(value));
                    } else {
                        // Unknown constant - warn but don't transform
                        System.err.println("Warning: Unknown Tv constant: Tv." + constantName);
                    }
                }
            }
            return (Node) super.visit(fieldAccess, arg);
        }
    }
}