package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        Files.walkFileTree(sourceDir, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    processJavaFile(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Failed to visit file: " + file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    private static void processJavaFile(Path file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse: " + file)
        );
        
        boolean modified = false;
        
        // 1. Check if file uses org.cactoos.map.MapEntry (import or fully qualified)
        boolean hasCactoosMapEntryImport = false;
        java.util.List<ImportDeclaration> importsToRemove = new java.util.ArrayList<>();
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.cactoos.map.MapEntry")) {
                hasCactoosMapEntryImport = true;
                importsToRemove.add(importDecl);
            }
        }
        
        // 2. Replace new MapEntry<>("key", "value") with Map.entry("key", "value")
        // Check all MapEntry object creations
        for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
            if (expr.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = expr.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("MapEntry")) {
                    // Check if it's org.cactoos.map.MapEntry (has scope or we have the import)
                    boolean isCactoosMapEntry = type.getScope()
                        .map(scope -> scope.asString().equals("org.cactoos.map"))
                        .orElse(false) || hasCactoosMapEntryImport;
                    
                    if (isCactoosMapEntry) {
                        // Replace new MapEntry<>("key", "value") with Map.entry("key", "value")
                        NodeList<com.github.javaparser.ast.expr.Expression> arguments = expr.getArguments();
                        MethodCallExpr replacement = new MethodCallExpr(
                            new NameExpr("Map"),
                            "entry",
                            arguments
                        );
                        expr.replace(replacement);
                        modified = true;
                    }
                }
            }
        }
        
        // 3. Remove import and add java.util.Map import if we made changes
        if (modified) {
            // Remove org.cactoos.map.MapEntry imports
            for (ImportDeclaration importDecl : importsToRemove) {
                cu.remove(importDecl);
            }
            
            // Add import for java.util.Map if not already present
            if (cu.getImports().stream().noneMatch(imp -> imp.getNameAsString().equals("java.util.Map"))) {
                cu.addImport("java.util.Map");
            }
            
            // Write back the modified file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            String newContent = new DefaultPrettyPrinter(config).print(cu);
            Files.write(file, newContent.getBytes());
            System.out.println("Modified: " + file);
        }
    }
}