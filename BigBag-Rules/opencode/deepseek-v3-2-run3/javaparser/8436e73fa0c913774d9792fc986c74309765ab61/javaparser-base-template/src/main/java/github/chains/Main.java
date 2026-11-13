package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming JAX-WS to Jakarta JWS API in: " + sourceDir);
        
        try {
            transformJaxWsToJakarta(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformJaxWsToJakarta(String sourceDir) throws IOException {
        List<Path> javaFiles = findJavaFiles(sourceDir);
        JavaParser javaParser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile);
            CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + javaFile)
            );
            
            boolean modified = false;
            
            // Transform imports
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith("javax.jws")) {
                    String newImportName = importName.replaceFirst("^javax\\.jws", "jakarta.jws");
                    importDecl.setName(newImportName);
                    modified = true;
                    System.out.println("  Updated import: " + importName + " -> " + newImportName);
                }
            }
            
            // Transform fully-qualified names in code
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(Name n, Void arg) {
                    String nameStr = n.asString();
                    if (nameStr.startsWith("javax.jws.")) {
                        String newName = nameStr.replaceFirst("^javax\\.jws\\.", "jakarta.jws.");
                        n.setIdentifier(newName.substring(newName.lastIndexOf('.') + 1));
                        return n;
                    }
                    return super.visit(n, arg);
                }
            }, null);
            
            if (modified) {
                saveCompilationUnit(cu, javaFile);
                System.out.println("  Saved modifications to: " + javaFile);
            }
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> stream = Files.walk(Paths.get(sourceDir))) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static void saveCompilationUnit(CompilationUnit cu, Path filePath) throws IOException {
        PrinterConfiguration config = new DefaultPrinterConfiguration();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
        String content = printer.print(cu);
        
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(content);
        }
    }
}