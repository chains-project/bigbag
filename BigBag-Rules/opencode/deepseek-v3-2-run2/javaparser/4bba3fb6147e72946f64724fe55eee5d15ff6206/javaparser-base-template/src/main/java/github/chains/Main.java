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
    
    // Configuration parameters - these can be changed for different projects
    private static final String OLD_PACKAGE_PREFIX = "javax.annotation";
    private static final String NEW_PACKAGE_PREFIX = "jakarta.annotation";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Transforming javax.annotation imports to jakarta.annotation in: " + sourceDirectory);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDirectory));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            int transformedImports = 0;
            
            for (Path javaFile : javaFiles) {
                int importsTransformedInFile = transformFile(javaFile);
                if (importsTransformedInFile > 0) {
                    transformedFiles++;
                    transformedImports += importsTransformedInFile;
                }
            }
            
            System.out.println("Transformation completed:");
            System.out.println("  - Files modified: " + transformedFiles);
            System.out.println("  - Imports transformed: " + transformedImports);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path rootDir) throws IOException {
        return Files.walk(rootDir)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static int transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        ImportTransformer transformer = new ImportTransformer();
        cu.accept(transformer, null);
        
        if (transformer.getTransformedCount() > 0) {
            Files.write(javaFile, cu.toString().getBytes());
            System.out.println("  Modified: " + javaFile + " (" + transformer.getTransformedCount() + " imports)");
        }
        
        return transformer.getTransformedCount();
    }
    
    private static class ImportTransformer extends ModifierVisitor<Void> {
        private int transformedCount = 0;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                
                ImportDeclaration newImport = new ImportDeclaration(
                        new Name(newImportName),
                        importDecl.isStatic(),
                        importDecl.isAsterisk()
                );
                
                transformedCount++;
                return newImport;
            }
            
            return super.visit(importDecl, arg);
        }
        
        public int getTransformedCount() {
            return transformedCount;
        }
    }
}