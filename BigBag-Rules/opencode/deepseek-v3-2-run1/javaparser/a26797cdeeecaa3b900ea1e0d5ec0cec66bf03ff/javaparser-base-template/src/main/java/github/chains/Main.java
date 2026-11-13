package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        
        List<File> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser javaParser = new JavaParser();
        int transformedFiles = 0;
        
        for (File file : javaFiles) {
            try {
                CompilationUnit cu = javaParser.parse(file).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + file);
                    continue;
                }
                
                LogbackTransformationVisitor visitor = new LogbackTransformationVisitor();
                cu.accept(visitor, null);
                
                if (visitor.wasTransformed()) {
                    writeFile(file, cu);
                    transformedFiles++;
                    System.out.println("Transformed: " + file);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + file + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    private static List<File> findJavaFiles(String dir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(dir), 10)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        }
    }
    
    private static void writeFile(File file, CompilationUnit cu) throws IOException {
        PrinterConfiguration config = new DefaultPrinterConfiguration();
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
        String content = printer.print(cu);
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
    
    static class LogbackTransformationVisitor extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public Node visit(ImportDeclaration n, Void arg) {
            // Remove imports of ch.qos.logback.classic.Logger
            if (n.getNameAsString().equals("ch.qos.logback.classic.Logger")) {
                transformed = true;
                return null; // Remove the import
            }
            return (Node) super.visit(n, arg);
        }
        
        @Override 
        public Node visit(ClassOrInterfaceType n, Void arg) {
            // Check for type references to ch.qos.logback.classic.Logger
            if (n.getNameAsString().equals("Logger")) {
                Optional<ClassOrInterfaceType> scope = n.getScope();
                if (scope.isPresent()) {
                    ClassOrInterfaceType scopeType = scope.get();
                    // Check if scope is ch.qos.logback.classic
                    String scopeName = scopeType.getNameAsString();
                    if (scopeName.equals("classic")) {
                        Optional<ClassOrInterfaceType> outerScope = scopeType.getScope();
                        if (outerScope.isPresent() && outerScope.get().getNameAsString().equals("ch.qos.logback")) {
                            transformed = true;
                            // Change to org.slf4j.Logger
                            n.setName("org.slf4j.Logger");
                            n.removeScope();
                        }
                    } else if (scopeName.equals("ch.qos.logback.classic")) {
                        transformed = true;
                        // Change to org.slf4j.Logger
                        n.setName("org.slf4j.Logger");
                        n.removeScope();
                    }
                }
            }
            return (Node) super.visit(n, arg);
        }
    }
}