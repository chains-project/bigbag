package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = new ArrayList<>();
        try {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        JavaParser javaParser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                com.github.javaparser.ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
                if (!parseResult.isSuccessful()) {
                    System.err.println("Failed to parse " + javaFile);
                    continue;
                }
                
                CompilationUnit cu = parseResult.getResult().orElseThrow();
                SnakeYamlTransformer transformer = new SnakeYamlTransformer();
                int transformations = transformer.transform(cu);
                
                if (transformations > 0) {
                    // Add import for LoaderOptions if needed
                    boolean hasLoaderOptionsImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("org.yaml.snakeyaml.LoaderOptions"));
                    if (!hasLoaderOptionsImport && transformer.needsLoaderOptionsImport) {
                        cu.addImport("org.yaml.snakeyaml.LoaderOptions");
                    }
                    
                    try {
                        Files.write(javaFile, cu.toString().getBytes());
                    } catch (IOException e) {
                        System.err.println("Error writing file " + javaFile + ": " + e.getMessage());
                    }
                    transformedFiles++;
                    totalTransformations += transformations;
                    System.out.println("Transformed " + javaFile + " (" + transformations + " changes)");
                }
            } catch (IOException e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Summary: Transformed " + transformedFiles + " files with " + totalTransformations + " total transformations");
    }
    
    static class SnakeYamlTransformer extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        private boolean needsLoaderOptionsImport = false;
        
        public int transform(CompilationUnit cu) {
            transformationCount = 0;
            needsLoaderOptionsImport = false;
            cu.accept(this, null);
            return transformationCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a snakeyaml class that now requires LoaderOptions
            String typeName = n.getType().getNameAsString();
            
            // List of snakeyaml classes that now require LoaderOptions parameter
            // based on snakeyaml 2.0 API specification
            if (typeName.equals("Constructor") || typeName.equals("SafeConstructor") || typeName.equals("Yaml")) {
                // Check if it's the no-arg constructor
                if (n.getArguments().isEmpty()) {
                    transformationCount++;
                    needsLoaderOptionsImport = true;
                    
                    // For a truly generic transformation, we should create:
                    // LoaderOptions options = new LoaderOptions();
                    // options.setTagInspector(tag -> true);
                    // new ClassName(options)
                    // But we can't insert multiple statements here.
                    // Instead, we'll create a simple LoaderOptions() for now.
                    // Projects might need manual adjustment for tag inspector.
                    
                    ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                    loaderOptions.setType(new ClassOrInterfaceType(null, "LoaderOptions"));
                    
                    // Replace with new ClassName(new LoaderOptions())
                    return n.clone()
                            .setArguments(new com.github.javaparser.ast.NodeList<>(loaderOptions));
                }
            }
            
            return super.visit(n, arg);
        }
    }
}