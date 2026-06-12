package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path sourcePath = Paths.get(sourceDirectory);
        
        try {
            // Process all Java files in the source directory
            Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Check if the file has the problematic import
            boolean hasOldImport = cu.getImports().stream()
                .anyMatch(importDecl -> 
                    importDecl.getNameAsString().equals("net.lingala.zip4j.core.ZipFile"));
            
            if (hasOldImport) {
                // Replace the import
                NodeList<ImportDeclaration> imports = cu.getImports();
                for (int i = 0; i < imports.size(); i++) {
                    ImportDeclaration importDecl = imports.get(i);
                    if (importDecl.getNameAsString().equals("net.lingala.zip4j.core.ZipFile")) {
                        // Replace with the new import
                        ImportDeclaration newImport = StaticJavaParser.parseImport(
                            "import net.lingala.zip4j.ZipFile;");
                        imports.set(i, newImport);
                        break;
                    }
                }
                
                // Save the file back
                cu.setStorage(filePath);
                cu.toString();
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("Fixed file: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
}
