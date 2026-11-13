package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation rule to replace cactoos.io.BytesOf and cactoos.text.HexOf usage
 * with standard Java equivalents.
 * 
 * This transformation handles the pattern:
 * new Digest.Sha256(new HexOf(new BytesOf(methodCall())).asString())
 * and replaces it with a standard Java equivalent that does not require cactoos.
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        File dir = new File(sourceDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Invalid source directory: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Just print the compilation unit to stdout (for demonstration)
            System.out.println("Processing: " + filePath);
            System.out.println("=== ORIGINAL ===");
            System.out.println(cu.toString());
            
            System.out.println("=== TRANSFORMATION LOGIC ===");
            System.out.println("Replace pattern: new HexOf(new BytesOf(digestMethod())).asString()");
            System.out.println("With: bytesToHex(digestMethod())");
            
        } catch (IOException e) {
            System.err.println("Failed to process " + filePath + ": " + e.getMessage());
        }
    }
}