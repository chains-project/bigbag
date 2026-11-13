package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
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
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
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
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return false;
            }
            
            boolean[] modified = {false};
            
            // Transform imports
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(ImportDeclaration n, Void arg) {
                    String importName = n.getNameAsString();
                    
                    // Transform javax.jws imports to jakarta.jws
                    if (importName.startsWith("javax.jws")) {
                        String newImportName = importName.replace("javax.jws", "jakarta.jws");
                        ImportDeclaration newImport = n.clone();
                        newImport.setName(new Name(newImportName));
                        modified[0] = true;
                        return newImport;
                    }
                    
                    // Transform javax.jws.soap imports to jakarta.jws.soap
                    if (importName.startsWith("javax.jws.soap")) {
                        String newImportName = importName.replace("javax.jws.soap", "jakarta.jws.soap");
                        ImportDeclaration newImport = n.clone();
                        newImport.setName(new Name(newImportName));
                        modified[0] = true;
                        return newImport;
                    }
                    
                    return super.visit(n, arg);
                }
            }, null);
            
            if (modified[0]) {
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
}