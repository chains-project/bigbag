package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        try (Stream<Path> stream = Files.walk(startDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        try {
            String content = Files.readString(javaFile);
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElse(null);
            
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                return false;
            }
            
            final boolean[] wasModified = new boolean[1];
            
            // Transform imports
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith("javax.validation.")) {
                    String newImportName = importName.replace("javax.validation.", "jakarta.validation.");
                    importDecl.setName(new Name(newImportName));
                    wasModified[0] = true;
                    System.out.println("  Updated import: " + importName + " -> " + newImportName);
                }
            }
            
            // Transform fully-qualified type references in the code using a visitor
            cu.walk(Name.class, name -> {
                String nameStr = name.asString();
                if (nameStr.startsWith("javax.validation.")) {
                    String newName = nameStr.replace("javax.validation.", "jakarta.validation.");
                    name.setIdentifier(newName);
                    wasModified[0] = true;
                }
            });
            
            if (wasModified[0]) {
                // Write back the transformed file
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                String transformed = printer.print(cu);
                Files.writeString(javaFile, transformed);
                System.out.println("  Updated file: " + javaFile);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("Error transforming file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}