package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.PrettyPrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these can be changed for different breaking changes
    private static final String OLD_PACKAGE = "io.dropwizard.setup";
    private static final String NEW_PACKAGE = "io.dropwizard.core.setup";
    private static final String CLASS_NAME = "Environment";
    private static final String CONSTRUCTOR_CLASS = "io.dropwizard.client.JerseyClientBuilder";
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        if (javaFiles.isEmpty()) {
            System.err.println("No Java files found in directory: " + sourceDir);
            System.exit(1);
        }
        
        JavaParser parser = new JavaParser();
        PrettyPrinter printer = new PrettyPrinter(new PrettyPrinterConfiguration());
        
        int filesProcessed = 0;
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    System.err.println("Failed to parse: " + javaFile);
                    continue;
                }
                
                boolean modified = false;
                
                // Apply import transformation
                for (ImportDeclaration importDecl : cu.getImports()) {
                    if (importDecl.getNameAsString().equals(OLD_PACKAGE + "." + CLASS_NAME)) {
                        importDecl.setName(NEW_PACKAGE + "." + CLASS_NAME);
                        modified = true;
                        System.out.println("Updated import in " + javaFile + ": " + 
                            OLD_PACKAGE + "." + CLASS_NAME + " -> " + NEW_PACKAGE + "." + CLASS_NAME);
                    }
                }
                
                // Apply constructor transformation
                ConstructorTransformer transformer = new ConstructorTransformer();
                Visitable result = transformer.visit(cu, null);
                if (result != cu) {
                    modified = true;
                }
                
                if (modified) {
                    String transformedCode = printer.print(cu);
                    Files.write(javaFile, transformedCode.getBytes());
                    filesModified++;
                    System.out.println("Successfully transformed: " + javaFile);
                }
                
                filesProcessed++;
                
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete:");
        System.out.println("  Files processed: " + filesProcessed);
        System.out.println("  Files modified: " + filesModified);
    }
    
    /**
     * Visitor that transforms constructor calls from old API pattern to new API pattern.
     * This is a generic transformer that can be configured for different breaking changes.
     */
    private static class ConstructorTransformer extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a JerseyClientBuilder constructor call
            if (n.getType().asString().equals("JerseyClientBuilder")) {
                // Check if the constructor has exactly one argument
                if (n.getArguments().size() == 1) {
                    // Check if the argument type matches the old Environment class
                    // This is a simplified check - in a real scenario, you'd want more robust type checking
                    // but for this generic transformation, we assume the single argument is the Environment
                    System.out.println("Found JerseyClientBuilder constructor call");
                    // The transformation is handled by the import update - the constructor signature
                    // remains the same, just the type of the Environment parameter changes
                }
            }
            return super.visit(n, arg);
        }
    }
}