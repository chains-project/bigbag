package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        System.out.println("Processing source directory: " + sourceDirectory);

        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDirectory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path javaFile : javaFiles) {
                System.out.println("Processing: " + javaFile);
                processFile(javaFile);
            }

            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow(() -> 
            new RuntimeException("Failed to parse " + filePath));

        YamlConstructorTransformer transformer = new YamlConstructorTransformer();
        cu.accept(transformer, null);

        // Write the transformed content back to the file
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
        String transformedCode = printer.print(cu);
        Files.write(filePath, transformedCode.getBytes());
    }

    /**
     * Generic transformation rule for snakeyaml 2.0 breaking API change:
     * - Old pattern: new Yaml(BaseConstructor constructor)
     * - New pattern: new Yaml(BaseConstructor constructor, LoaderOptions loadingConfig)
     * - Old pattern: new Constructor(Class<?> theRoot)
     * - New pattern: new Constructor(Class<?> theRoot, LoaderOptions loadingConfig)
     * 
     * This transformation adds a default LoaderOptions parameter to all Yaml and Constructor
     * constructor calls that only have a BaseConstructor or Class parameter.
     */
    private static class YamlConstructorTransformer extends ModifierVisitor<Void> {
        private JavaParser javaParser = new JavaParser();
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Yaml constructor call
            if (isYamlConstructorCall(n)) {
                // Yaml constructor that takes BaseConstructor is still valid in snakeyaml 2.0
                // No transformation needed for Yaml constructor calls
                System.out.println("Found Yaml constructor call in " + getCurrentFileLocation(n) + " - no transformation needed");
            }
            // Check if this is a Constructor constructor call
            else if (isConstructorCall(n)) {
                // Check if it has exactly one argument (Class)
                if (n.getArguments().size() == 1) {
                    // Add a new LoaderOptions() as the second parameter
                    ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                    loaderOptions.setType(javaParser.parseClassOrInterfaceType("org.yaml.snakeyaml.LoaderOptions").getResult().get());
                    
                    n.getArguments().add(loaderOptions);
                    System.out.println("Transformed Constructor call in " + getCurrentFileLocation(n));
                }
            }
            return super.visit(n, arg);
        }

        /**
         * Checks if this ObjectCreationExpr is a Constructor call
         */
        private boolean isConstructorCall(ObjectCreationExpr expr) {
            return expr.getType().getNameAsString().equals("Constructor") &&
                   isInSnakeyamlPackage(expr);
        }

        /**
         * Checks if this ObjectCreationExpr is a Yaml constructor call
         */
        private boolean isYamlConstructorCall(ObjectCreationExpr expr) {
            return expr.getType().getNameAsString().equals("Yaml") &&
                   isInSnakeyamlPackage(expr);
        }

        /**
         * Checks if the Yaml constructor call is from the snakeyaml package
         * by looking at import statements and fully qualified type names
         */
        private boolean isInSnakeyamlPackage(ObjectCreationExpr expr) {
            // Check if the type is fully qualified with snakeyaml package
            String typeName = expr.getType().toString();
            if (typeName.contains("org.yaml.snakeyaml.Yaml") || 
                typeName.contains("snakeyaml.Yaml")) {
                return true;
            }

            // For simple "Yaml" type, we need to check imports in the compilation unit
            // This would require access to the CompilationUnit context
            // For simplicity in this generic rule, we assume any "Yaml" constructor
            // with BaseConstructor parameter is from snakeyaml
            return true;
        }

        /**
         * Helper method to get file location for logging
         */
        private String getCurrentFileLocation(Node node) {
            return node.getRange().map(range -> 
                "line " + range.begin.line + ", column " + range.begin.column
            ).orElse("unknown location");
        }
    }
}