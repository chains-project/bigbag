package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static boolean modified = false;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformed = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformed++;
                }
            }
            
            System.out.println("Transformed " + transformed + " files successfully");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path start) throws IOException {
        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        modified = false;
        cu.accept(new ConstructorTransformer(), null);
        
        if (modified) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
            String transformed = printer.print(cu);
            Files.writeString(filePath, transformed);
            System.out.println("Transformed: " + filePath);
            return true;
        }
        
        return false;
    }
    
    private static class ConstructorTransformer extends ModifierVisitor<Void> {
        @Override
        public ObjectCreationExpr visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Check if this is a org.yaml.snakeyaml.constructor.Constructor object creation
            String typeName = expr.getType().toString();
            
            // Handle both simple name "Constructor" and fully qualified name
            if (typeName.equals("Constructor") || 
                typeName.equals("org.yaml.snakeyaml.constructor.Constructor") ||
                typeName.startsWith("Constructor<")) {
                
                // Check if it has exactly one argument (the old API)
                if (expr.getArguments().size() == 1) {
                    System.out.println("Found single-argument Constructor call to transform: " + expr);
                    
                    // Create new LoaderOptions() expression
                    ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                    loaderOptions.setType("org.yaml.snakeyaml.LoaderOptions");
                    
                    // Add the LoaderOptions as second argument
                    expr.getArguments().add(loaderOptions);
                    modified = true;
                }
            }
            
            // Also check for SafeConstructor which has similar API change
            if (typeName.equals("SafeConstructor") || 
                typeName.equals("org.yaml.snakeyaml.constructor.SafeConstructor")) {
                
                // SafeConstructor old API: SafeConstructor() with no args
                // New API: SafeConstructor(LoaderOptions)
                if (expr.getArguments().isEmpty()) {
                    System.out.println("Found zero-argument SafeConstructor call to transform: " + expr);
                    
                    // Create new LoaderOptions() expression
                    ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                    loaderOptions.setType("org.yaml.snakeyaml.LoaderOptions");
                    
                    // Add the LoaderOptions as argument
                    expr.getArguments().add(loaderOptions);
                    modified = true;
                }
            }
            
            return expr;
        }
    }
}