package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source_directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            processDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }
    
    private static void processFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(() -> 
                new IOException("Failed to parse " + file));
            
            SnakeYamlTransformer transformer = new SnakeYamlTransformer(cu);
            cu.accept(transformer, null);
            
            if (transformer.isModified()) {
                Files.write(file, cu.toString().getBytes());
                System.out.println("Modified: " + file);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + file + ": " + e.getMessage());
        }
    }
    
    private static class SnakeYamlTransformer extends ModifierVisitor<Void> {
        private final CompilationUnit cu;
        private boolean modified = false;
        private boolean hasSnakeYamlImport = false;
        private boolean hasConstructorImport = false;
        private boolean hasLoaderOptionsImport = false;
        
        public SnakeYamlTransformer(CompilationUnit cu) {
            this.cu = cu;
            checkImports();
        }
        
        private void checkImports() {
            for (ImportDeclaration importDecl : cu.getImports()) {
                String importName = importDecl.getNameAsString();
                if (importName.startsWith("org.yaml.snakeyaml")) {
                    hasSnakeYamlImport = true;
                }
                if (importName.equals("org.yaml.snakeyaml.constructor.Constructor") ||
                    importName.endsWith(".Constructor") && importName.contains("snakeyaml")) {
                    hasConstructorImport = true;
                }
                if (importName.equals("org.yaml.snakeyaml.LoaderOptions") ||
                    importName.endsWith(".LoaderOptions") && importName.contains("snakeyaml")) {
                    hasLoaderOptionsImport = true;
                }
            }
        }
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a snakeyaml Constructor object creation
            if (isSnakeYamlConstructorCreation(n)) {
                transformConstructorCall(n);
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isSnakeYamlConstructorCreation(ObjectCreationExpr n) {
            String typeName = n.getType().asString();
            
            // Check if it's definitely snakeyaml Constructor
            if (typeName.equals("org.yaml.snakeyaml.constructor.Constructor")) {
                return true;
            }
            
            // Check if it could be imported Constructor
            if (typeName.equals("Constructor")) {
                // We need to check if there's a snakeyaml import
                // This is a simplified check - real implementation would need symbol resolution
                return hasSnakeYamlImport || hasConstructorImport;
            }
            
            return false;
        }
        
        private void transformConstructorCall(ObjectCreationExpr n) {
            // Ensure LoaderOptions import exists
            if (!hasLoaderOptionsImport) {
                cu.addImport("org.yaml.snakeyaml.LoaderOptions");
                hasLoaderOptionsImport = true;
            }
            
            // Create new LoaderOptions() expression
            ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
            loaderOptions.setType("LoaderOptions");
            
            // Transform based on number of arguments
            if (n.getArguments().isEmpty()) {
                // Case: new Constructor() -> new Constructor(new LoaderOptions())
                n.getArguments().add(loaderOptions);
                modified = true;
            } else if (n.getArguments().size() == 1) {
                // Check if already has LoaderOptions parameter
                Expression firstArg = n.getArguments().get(0);
                if (!(firstArg instanceof ObjectCreationExpr) || 
                    !((ObjectCreationExpr)firstArg).getType().toString().contains("LoaderOptions")) {
                    // Case: new Constructor(X) -> new Constructor(X, new LoaderOptions())
                    n.getArguments().add(loaderOptions);
                    modified = true;
                }
            }
            // If 2+ arguments, assume already converted
        }
    }
}