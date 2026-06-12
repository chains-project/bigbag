package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    private static final String OLD_PACKAGE_PREFIX = "net.lingala.zip4j.core";
    private static final String NEW_PACKAGE_PREFIX = "net.lingala.zip4j";
    private static final String TARGET_CLASS_NAME = "ZipFile";
    
    private static final String OLD_FULLY_QUALIFIED_NAME = OLD_PACKAGE_PREFIX + "." + TARGET_CLASS_NAME;
    private static final String NEW_FULLY_QUALIFIED_NAME = NEW_PACKAGE_PREFIX + "." + TARGET_CLASS_NAME;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully updated " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + filePath);
                return false;
            }
            
            boolean modified = false;
            
            modified = updateImports(cu) || modified;
            
            if (modified) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
                String updatedContent = printer.print(cu);
                
                Files.write(filePath, updatedContent.getBytes());
                System.out.println("Updated: " + filePath);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            return false;
        }
    }
    
    private static boolean updateImports(CompilationUnit cu) {
        boolean modified = false;
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            if (importName.equals(OLD_FULLY_QUALIFIED_NAME)) {
                importDecl.setName(NEW_FULLY_QUALIFIED_NAME);
                modified = true;
                System.out.println("  Updated import: " + importName + " -> " + NEW_FULLY_QUALIFIED_NAME);
            }
        }
        
        return modified;
    }
}
