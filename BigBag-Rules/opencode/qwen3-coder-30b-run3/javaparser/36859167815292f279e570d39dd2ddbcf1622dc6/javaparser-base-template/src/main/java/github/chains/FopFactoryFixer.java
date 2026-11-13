package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.resolution.types.ResolvedType;
import java.nio.file.*;
import java.util.*;

public class FopFactoryFixer {
    
    /**
     * Fixes FopFactory.newInstance() calls without arguments to be compatible with FOP 2.2+
     * This addresses the breaking change where newInstance() with no arguments was removed
     * 
     * Pattern matched: FopFactory.newInstance()
     * Replacement: FopFactory.getDefaultInstance()
     */
    public static void fixFopFactoryNewInstanceCalls(String sourceDir) {
        try {
            // Process all Java files in the source directory recursively
            Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(FopFactoryFixer::processJavaFile);
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = StaticJavaParser.parse(content);
            
            // Find all FopFactory.newInstance() calls without arguments
            boolean modified = false;
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (isFopFactoryNewInstanceWithoutArgs(call)) {
                    replaceWithCompatibleVersion(call);
                    modified = true;
                }
            }
            
            // Save the modified content back to file if changes were made
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Fixed file: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean isFopFactoryNewInstanceWithoutArgs(MethodCallExpr call) {
        // Check if it's a call to FopFactory.newInstance with no arguments
        return call.getScope().filter(scope -> 
            scope instanceof NameExpr && 
            ((NameExpr) scope).getNameAsString().equals("FopFactory")
        ).isPresent() && 
        call.getNameAsString().equals("newInstance") &&
        call.getArguments().isEmpty();
    }
    
    private static void replaceWithCompatibleVersion(MethodCallExpr call) {
        // Replace FopFactory.newInstance() with FopFactory.getDefaultInstance()
        // This is the proper FOP 2.2+ approach
        call.setScope(new NameExpr("FopFactory"));
        call.setName("getDefaultInstance");
        call.setArguments(new NodeList<>());
    }
}