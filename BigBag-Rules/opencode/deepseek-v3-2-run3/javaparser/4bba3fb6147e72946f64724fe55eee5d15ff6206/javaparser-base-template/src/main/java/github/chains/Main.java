package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    // Configuration: map of old package prefixes to new package prefixes
    private static final Map<String, String> PACKAGE_MAPPINGS = new HashMap<>();
    
    static {
        // Example: javax.annotation -> jakarta.annotation migration
        PACKAGE_MAPPINGS.put("javax.annotation", "jakarta.annotation");
        // Add more mappings as needed for other breaking changes
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("This tool automatically migrates imports based on configured package mappings:");
            for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
                System.err.println("  " + entry.getKey() + " -> " + entry.getValue());
            }
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Active package mappings:");
        for (Map.Entry<String, String> entry : PACKAGE_MAPPINGS.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
        }
        
        try {
            List<Path> javaFiles = findAllJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findAllJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        boolean[] modified = {false};
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration importDecl, Void arg) {
                Name importName = importDecl.getName();
                String importStr = importName.asString();
                
                // Check each package mapping
                for (Map.Entry<String, String> mapping : PACKAGE_MAPPINGS.entrySet()) {
                    String oldPrefix = mapping.getKey();
                    String newPrefix = mapping.getValue();
                    
                    if (importStr.startsWith(oldPrefix)) {
                        String newImport = importStr.replaceFirst("^" + escapeRegex(oldPrefix), newPrefix);
                        importDecl.setName(new Name(newImport));
                        modified[0] = true;
                        System.out.println("  Modified import: " + importStr + " -> " + newImport);
                        break; // Only apply first matching mapping
                    }
                }
                
                return super.visit(importDecl, arg);
            }
        }, null);
        
        if (modified[0]) {
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String newContent = printer.print(cu);
            Files.write(javaFile, newContent.getBytes());
            System.out.println("Updated file: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    private static String escapeRegex(String str) {
        // Escape special regex characters for use in replaceFirst
        return str.replace(".", "\\.");
    }
}