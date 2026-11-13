package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Applies Jakarta EE namespace migration: javax.mvc -> jakarta.mvc");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist or is not a directory: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying Jakarta EE MVC namespace migration to: " + sourceDir);
        System.out.println("Transformation: javax.mvc -> jakarta.mvc");
        
        try (Stream<Path> paths = Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS)) {
            paths.filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("Transformation completed.");
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Warning: Could not parse " + javaFile);
                return;
            }
            
            boolean modified = false;
            NodeList<ImportDeclaration> imports = cu.getImports();
            
            for (int i = 0; i < imports.size(); i++) {
                ImportDeclaration importDecl = imports.get(i);
                String importStr = importDecl.getNameAsString();
                
                if (importStr.startsWith("javax.mvc")) {
                    String newImportStr = importStr.replaceFirst("^javax\\.mvc", "jakarta.mvc");
                    ImportDeclaration newImport = new ImportDeclaration(newImportStr, importDecl.isStatic(), importDecl.isAsterisk());
                    imports.set(i, newImport);
                    modified = true;
                    System.out.println("  Updated import: " + importStr + " -> " + newImportStr);
                }
            }
            
            if (modified) {
                PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
                config.setPrintComments(true);
                config.setEndOfLineCharacter("\n");
                
                PrettyPrinter printer = new PrettyPrinter(config);
                String updatedContent = printer.print(cu);
                
                Files.write(javaFile, updatedContent.getBytes());
                System.out.println("  Updated file: " + javaFile);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}