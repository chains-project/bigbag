package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <source_directory>");
            return;
        }
        
        String sourceDir = args[0];
        try {
            fixMinaBreakingChanges(sourceDir);
            System.out.println("Successfully processed files in " + sourceDir);
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply generic transformations to fix Mina-core breaking changes
     */
    public static void fixMinaBreakingChanges(String sourceDirectory) throws IOException {
        Path dir = Paths.get(sourceDirectory);
        if (!Files.exists(dir)) {
            throw new IOException("Source directory does not exist: " + sourceDirectory);
        }

        // Walk through all Java files and apply transformations
        Files.walk(dir, 10) // Max depth of 10
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }

    /**
     * Process individual Java files to fix Mina breaking changes
     */
    private static void processJavaFile(Path javaFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile.toFile());
            
            // Transformations for Mina breaking changes
            fixRemovedMethods(cu);
            fixMissingConstants(cu);
            
            // Save the modified file back
            Path outputPath = javaFile;
            Files.write(outputPath, LexicalPreservingPrinter.print(cu).getBytes());
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }

    /**
     * Fix calls to removed methods (like initiateHandshake, setUseClientMode)
     */
    private static void fixRemovedMethods(CompilationUnit cu) {
        // Remove calls to initiateHandshake method
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String methodName = methodCall.getNameAsString();
            if ("initiateHandshake".equals(methodName)) {
                // Remove the entire statement containing the call
                Optional<?> parent = methodCall.getParentNode();
                if (parent.isPresent()) {
                    Object parentNode = parent.get();
                    if (parentNode instanceof ExpressionStmt) {
                        ((ExpressionStmt) parentNode).remove();
                    }
                }
            }
            
            // Remove calls to setUseClientMode method
            if ("setUseClientMode".equals(methodName)) {
                // Remove the entire statement containing the call
                Optional<?> parent = methodCall.getParentNode();
                if (parent.isPresent()) {
                    Object parentNode = parent.get();
                    if (parentNode instanceof ExpressionStmt) {
                        ((ExpressionStmt) parentNode).remove();
                    }
                }
            }
        });
    }

    /**
     * Fix references to missing constants like PEER_ADDRESS
     */
    private static void fixMissingConstants(CompilationUnit cu) {
        // Remove references to PEER_ADDRESS constant
        cu.findAll(FieldAccessExpr.class).forEach(fieldAccess -> {
            if ("PEER_ADDRESS".equals(fieldAccess.getNameAsString())) {
                // Remove the entire statement containing the field access
                Optional<?> parent = fieldAccess.getParentNode();
                if (parent.isPresent()) {
                    Object parentNode = parent.get();
                    if (parentNode instanceof ExpressionStmt) {
                        ((ExpressionStmt) parentNode).remove();
                    }
                }
            }
        });
    }
}