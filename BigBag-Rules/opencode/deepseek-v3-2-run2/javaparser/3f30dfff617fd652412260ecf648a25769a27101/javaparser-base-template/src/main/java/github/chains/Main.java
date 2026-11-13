package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    private static final String OLD_PACKAGE = "javax.interceptor";
    private static final String NEW_PACKAGE = "jakarta.interceptor";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDirectory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            
            // Skip files that don't contain the old package
            if (!content.contains(OLD_PACKAGE)) {
                return false;
            }
            
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + filePath)
            );
            
            boolean modified = false;
            
            // Transform imports
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().startsWith(OLD_PACKAGE)) {
                    String newImportName = importDecl.getNameAsString().replace(OLD_PACKAGE, NEW_PACKAGE);
                    importDecl.setName(new Name(newImportName));
                    modified = true;
                }
            }
            
            // Transform fully qualified names in code
            cu.accept(new PackageNameTransformer(), null);
            
            if (modified) {
                String transformedContent = cu.toString();
                Files.writeString(filePath, transformedContent);
                System.out.println("Modified: " + filePath);
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
        
        return false;
    }
    
    private static class PackageNameTransformer extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(Name name, Void arg) {
            String nameStr = name.asString();
            if (nameStr.startsWith(OLD_PACKAGE)) {
                String newName = nameStr.replace(OLD_PACKAGE, NEW_PACKAGE);
                return new Name(newName);
            }
            return super.visit(name, arg);
        }
    }
}