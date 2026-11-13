/*
 * Generic transformation to fix breaking changes in Maven projects due to dependency updates
 * Specifically addresses removal of cactoos.io.BytesOf and cactoos.text.HexOf classes
 * 
 * Usage: java -cp target/classes:target/dependency/* github.chains.Main /path/to/project
 */

package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.imports.ImportDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the project to fix cactoos dependency breakage
        String projectRoot = args.length > 0 ? args[0] : "/workspace/docker-adapter";
        Path rootPath = Paths.get(projectRoot);
        
        System.out.println("Processing project: " + projectRoot);
        
        // Find all Java files recursively
        Files.walk(rootPath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
            
        System.out.println("Transformation complete!");
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            boolean modified = false;
            
            // Remove cactoos imports
            modified |= removeCactoosImports(cu);
            
            // Replace HexOf calls with standard hex conversion
            modified |= replaceHexOfWithHexFormat(cu);
            
            // Replace BytesOf calls with direct byte handling
            modified |= replaceBytesOfWithByteArray(cu);
            
            // Save file if modified
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Failed to process " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean removeCactoosImports(CompilationUnit cu) {
        boolean modified = false;
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        
        for (ImportDeclaration imp : cu.getImports()) {
            String importName = imp.getNameAsString();
            if (importName.startsWith("org.cactoos.")) {
                importsToRemove.add(imp);
                modified = true;
            }
        }
        
        for (ImportDeclaration imp : importsToRemove) {
            cu.remove(imp);
        }
        
        return modified;
    }
    
    private static boolean replaceHexOfWithHexFormat(CompilationUnit cu) {
        boolean modified = false;
        
        // Look for HexOf method calls that are being used for byte-to-hex conversion
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if ("HexOf".equals(methodCall.getNameAsString())) {
                // Replace HexOf calls - typically used as new Digest.Sha256(new HexOf(...))
                // We'll replace it with HexFormat.of().formatHex(...)
                try {
                    // Replace with direct hex conversion using standard library
                    Expression replacement = new MethodCallExpr(
                        new MethodCallExpr(
                            new NameExpr("java.util.HexFormat"),
                            "of"
                        ),
                        "formatHex",
                        methodCall.getArguments()
                    );
                    methodCall.replace(replacement);
                    modified = true;
                } catch (Exception ex) {
                    // If replacement fails, we still mark as modified
                    modified = true;
                }
            }
        });
        
        return modified;
    }
    
    private static boolean replaceBytesOfWithByteArray(CompilationUnit cu) {
        boolean modified = false;
        
        // Replace BytesOf calls with direct handling
        cu.findAll(MethodCallExpr.class).forEach(methodCall -> {
            if ("BytesOf".equals(methodCall.getNameAsString())) {
                // Just replace with the argument - BytesOf is typically used to wrap byte arrays
                NodeList<Expression> args = methodCall.getArguments();
                if (!args.isEmpty()) {
                    // Replace the entire BytesOf call with its argument
                    methodCall.replace(args.get(0));
                    modified = true;
                }
            }
        });
        
        return modified;
    }
}