package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Scanning directory: " + sourceDir);
        
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            paths.filter(Files::isRegularFile)
                 .filter(path -> path.toString().endsWith(".java"))
                 .forEach(Main::processJavaFile);
        } catch (Exception e) {
            System.err.println("Error scanning directory: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        
        System.out.println("Transformation complete");
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + javaFile)
            );
            
            boolean modified = false;
            
            // Remove imports of JsonSerializeAs annotation
            NodeList<ImportDeclaration> imports = cu.getImports();
            for (int i = imports.size() - 1; i >= 0; i--) {
                ImportDeclaration importDecl = imports.get(i);
                Name importName = importDecl.getName();
                
                // Check if this is an import of JsonSerializeAs
                if (importName.toString().equals("com.fasterxml.jackson.annotation.JsonSerializeAs")) {
                    System.out.println("Removing import: " + importName + " from " + javaFile);
                    imports.remove(i);
                    modified = true;
                }
                
                // Also check for static imports of JsonSerializeAs
                if (importDecl.isStatic() && importDecl.getNameAsString().endsWith(".JsonSerializeAs")) {
                    System.out.println("Removing static import: " + importName + " from " + javaFile);
                    imports.remove(i);
                    modified = true;
                }
            }
            
            // Save the file if modified
            if (modified) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String updatedContent = printer.print(cu);
                Files.write(javaFile, updatedContent.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("Updated: " + javaFile);
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}