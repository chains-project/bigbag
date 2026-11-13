package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Generic transformation to replace Tv constants from com.jcabi.aspects with their literal values.
 * Replaces:
 * - Tv.SEVEN with 7
 * - Tv.TEN with 10  
 * - Tv.MILLION with 1000000
 * And removes the import statement for com.jcabi.aspects.Tv
 */
public class TvConstantsTransformer {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: TvConstantsTransformer <source-file-path>");
            System.exit(1);
        }

        String filePath = args[0];
        try {
            transformFile(filePath);
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void transformFile(String filePath) throws IOException {
        // Parse the Java file
        CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath));
        
        // Find and replace Tv constants
        boolean modified = replaceTvConstants(cu);
        
        // Remove import statement for com.jcabi.aspects.Tv if present
        if (removeTvImport(cu)) {
            modified = true;
        }
        
        // If changes were made, save the file
        if (modified) {
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(cu.toString());
            }
            System.out.println("Transformed file: " + filePath);
        } else {
            System.out.println("No changes needed for: " + filePath);
        }
    }
    
    private static boolean replaceTvConstants(CompilationUnit cu) {
        boolean modified = false;
        
        // Look for field access expressions (like Tv.SEVEN)
        for (FieldAccessExpr fieldAccess : cu.findAll(FieldAccessExpr.class)) {
            // Check if this is accessing a Tv constant
            if (fieldAccess.getScope().toString().equals("Tv")) {
                String constantName = fieldAccess.getNameAsString();
                String replacementValue = getReplacementValue(constantName);
                
                if (replacementValue != null) {
                    // Replace with the integer literal
                    IntegerLiteralExpr replacement = new IntegerLiteralExpr(replacementValue);
                    fieldAccess.replace(replacement);
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    private static String getReplacementValue(String constantName) {
        switch (constantName) {
            case "SEVEN":
                return "7";
            case "TEN":
                return "10";
            case "MILLION":
                return "1000000";
            default:
                return null;
        }
    }
    
    private static boolean removeTvImport(CompilationUnit cu) {
        NodeList<ImportDeclaration> imports = cu.getImports();
        boolean modified = false;
        
        // Find the import statement for com.jcabi.aspects.Tv
        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration imp = imports.get(i);
            if (imp.getNameAsString().equals("com.jcabi.aspects.Tv")) {
                imports.remove(i);
                modified = true;
            }
        }
        
        return modified;
    }
}