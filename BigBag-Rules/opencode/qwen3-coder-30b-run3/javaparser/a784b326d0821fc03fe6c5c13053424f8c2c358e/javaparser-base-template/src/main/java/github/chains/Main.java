package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation to fix javax.validation to jakarta.validation migration
 * in Maven projects affected by hibernate-validator 8.x breaking change.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        // Process all Java files in the project
        String sourceDir = args.length > 0 ? args[0] : "/workspace/nem";
        
        System.out.println("Processing directory: " + sourceDir);
        
        // Find all Java files
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            boolean modified = transformCompilationUnit(cu);
            
            // If changes were made, save the file
            if (modified) {
                // Just save back to the original file using the parse and rewrite approach
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        // Transform imports
        modified |= transformImports(cu);
        
        return modified;
    }
    
    private static boolean transformImports(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all imports that start with "javax.validation"
        List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.validation")) {
                // Replace with jakarta.validation equivalent
                String newImport = importName.replace("javax.validation", "jakarta.validation");
                importDecl.setName(newImport);
                modified = true;
            }
        }
        
        return modified;
    }
}
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            boolean modified = transformCompilationUnit(cu);
            
            // If changes were made, save the file
            if (modified) {
                // Just save back to the original file using the parse and rewrite approach
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        // Transform imports
        modified |= transformImports(cu);
        
        // Transform annotations (this is just to indicate transformation occurred)
        modified |= transformAnnotations(cu);
        
        return modified;
    }
    
    private static boolean transformImports(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all imports that start with "javax.validation"
        List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.validation")) {
                // Replace with jakarta.validation equivalent
                String newImport = importName.replace("javax.validation", "jakarta.validation");
                importDecl.setName(newImport);
                modified = true;
            }
        }
        
        return modified;
    }
    
    private static boolean transformAnnotations(CompilationUnit cu) {
        // Find all @Valid annotations (they reference the javax.validation package)
        // Since the annotation itself doesn't change, we're mainly concerned with imports
        boolean modified = false;
        
        // Scan for annotations that would require the javax.validation import to be changed
        List<com.github.javaparser.ast.expr.NormalAnnotationExpr> normalAnnotations = 
            cu.findAll(com.github.javaparser.ast.expr.NormalAnnotationExpr.class);
        for (com.github.javaparser.ast.expr.NormalAnnotationExpr annotation : normalAnnotations) {
            if ("Valid".equals(annotation.getNameAsString())) {
                modified = true;
            }
        }
                
        List<com.github.javaparser.ast.expr.SingleMemberAnnotationExpr> singleAnnotations = 
            cu.findAll(com.github.javaparser.ast.expr.SingleMemberAnnotationExpr.class);
        for (com.github.javaparser.ast.expr.SingleMemberAnnotationExpr annotation : singleAnnotations) {
            if ("Valid".equals(annotation.getNameAsString())) {
                modified = true;
            }
        }
                
        return modified;
    }
}
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations
            boolean modified = transformCompilationUnit(cu);
            
            // If changes were made, save the file
            if (modified) {
                // Just save back to the original file using the parse and rewrite approach
                cu.toString(); // Get the string representation
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Updated: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        // Transform imports
        modified |= transformImports(cu);
        
        // Transform annotations (this is just to indicate transformation occurred)
        modified |= transformAnnotations(cu);
        
        return modified;
    }
    
    private static boolean transformImports(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all imports that start with "javax.validation"
        List<ImportDeclaration> imports = cu.findAll(ImportDeclaration.class);
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.validation")) {
                // Replace with jakarta.validation equivalent
                String newImport = importName.replace("javax.validation", "jakarta.validation");
                importDecl.setName(newImport);
                modified = true;
            }
        }
        
        return modified;
    }
    
    private static boolean transformAnnotations(CompilationUnit cu) {
        // Find all @Valid annotations (they reference the javax.validation package)
        // Since the annotation itself doesn't change, we're mainly concerned with imports
        boolean modified = false;
        
        // Scan for annotations that would require the javax.validation import to be changed
        cu.findAll(com.github.javaparser.ast.expr.NormalAnnotationExpr.class)
                .forEach(annotation -> {
                    if ("Valid".equals(annotation.getNameAsString())) {
                        modified = true;
                    }
                });
                
        cu.findAll(com.github.javaparser.ast.expr.SingleMemberAnnotationExpr.class)
                .forEach(annotation -> {
                    if ("Valid".equals(annotation.getNameAsString())) {
                        modified = true;
                    }
                });
                
        return modified;
    }
}
