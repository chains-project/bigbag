package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int transformedImports = 0;
            
            for (Path javaFile : javaFiles) {
                boolean fileModified = processFile(javaFile);
                if (fileModified) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedImports + " imports in " + transformedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            cu = parser.parse(in).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + javaFile));
        }
        
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            
            // Check if this is an org.assertj.core.internal.bytebuddy import
            if (importName.startsWith("org.assertj.core.internal.bytebuddy")) {
                // Transform to net.bytebuddy
                String newImportName = importName.replaceFirst(
                    "^org\\.assertj\\.core\\.internal\\.bytebuddy", 
                    "net.bytebuddy"
                );
                
                // Create new import declaration with same static flag
                ImportDeclaration newImport = new ImportDeclaration(
                    new Name(newImportName),
                    importDecl.isStatic(),
                    importDecl.isAsterisk()
                );
                
                // Replace the old import
                importDecl.replace(newImport);
                modified = true;
                
                System.out.println("Transformed import in " + javaFile + ":");
                System.out.println("  " + importName + " -> " + newImportName);
            }
        }
        
        if (modified) {
            // Write the modified file back
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String newContent = printer.print(cu);
            Files.write(javaFile, newContent.getBytes());
        }
        
        return modified;
    }
}