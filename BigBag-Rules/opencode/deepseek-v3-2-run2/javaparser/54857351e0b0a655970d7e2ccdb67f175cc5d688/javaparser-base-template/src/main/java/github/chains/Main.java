package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for zip4j 2.x breaking change:
 * Package structure changed from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
 * 
 * This transformation:
 * 1. Finds all import statements for net.lingala.zip4j.core.ZipFile
 * 2. Replaces them with net.lingala.zip4j.ZipFile
 * 3. Preserves all other imports and code structure
 * 
 * This is a generic rule that can be applied to any project affected by this breaking change
 * by simply changing the input source directory path.
 */
public class Main {
    
    // Configuration parameters - these can be changed to apply to different projects
    private static final String OLD_PACKAGE = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_PACKAGE = "net.lingala.zip4j.ZipFile";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying zip4j 2.x migration transformation to: " + sourceDirectory);
        
        try {
            int filesProcessed = processDirectory(Paths.get(sourceDirectory));
            System.out.println("Transformation completed. Processed " + filesProcessed + " Java files.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int processDirectory(Path directory) throws IOException {
        List<Path> javaFiles = Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        int filesProcessed = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                filesProcessed++;
            }
        }
        
        return filesProcessed;
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        try {
            JavaParser javaParser = new JavaParser();
            var parseResult = javaParser.parse(javaFile.toFile());
            
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse " + javaFile + ": " + parseResult.getProblems());
                return false;
            }
            
            CompilationUnit cu = parseResult.getResult().orElseThrow();
            boolean modified = false;
            
            // Check all import declarations
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                if (importDecl.getNameAsString().equals(OLD_PACKAGE)) {
                    // Replace the import with the new package
                    importDecl.setName(new Name(NEW_PACKAGE));
                    modified = true;
                    System.out.println("Updated import in " + javaFile + ": " + OLD_PACKAGE + " -> " + NEW_PACKAGE);
                }
            }
            
            if (modified) {
                // Use lexical preserving printer to maintain formatting
                String transformedCode = LexicalPreservingPrinter.print(cu);
                Files.write(javaFile, transformedCode.getBytes());
                return true;
            }
            
        } catch (FileNotFoundException e) {
            System.err.println("File not found: " + javaFile);
        }
        
        return false;
    }
}