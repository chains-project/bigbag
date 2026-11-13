package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing snakeyaml 1.31 breaking API changes.
 * 
 * This rule transforms Yaml constructor calls from the old signature:
 *   new Yaml(BaseConstructor, Representer, DumperOptions, Resolver)
 * to the new signature:
 *   new Yaml(BaseConstructor, Representer, DumperOptions, LoaderOptions, Resolver)
 * 
 * The transformation inserts a new LoaderOptions() parameter between the 
 * DumperOptions and Resolver parameters.
 */
public class Main {
    
    private static final String YAML_CLASS_NAME = "org.yaml.snakeyaml.Yaml";
    private static final String LOADER_OPTIONS_CLASS_NAME = "org.yaml.snakeyaml.LoaderOptions";
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        
        JavaParser javaParser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
                if (!parseResult.isSuccessful()) {
                    System.err.println("Failed to parse " + javaFile + ": " + parseResult.getProblems());
                    continue;
                }
                
                CompilationUnit cu = parseResult.getResult().orElseThrow();
                
                YamlConstructorTransformer transformer = new YamlConstructorTransformer();
                cu.accept(transformer, null);
                
                if (transformer.getTransformationCount() > 0) {
                    Files.write(javaFile, cu.toString().getBytes());
                    transformedFiles++;
                    transformedCalls += transformer.getTransformationCount();
                    System.out.println("Transformed " + transformer.getTransformationCount() + 
                                     " Yaml constructor calls in: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Transformed " + transformedCalls + " Yaml constructor calls in " + 
                         transformedFiles + " files");
    }
    
    /**
     * Visitor that transforms Yaml constructor calls from the old 4-parameter 
     * signature to the new 5-parameter signature by inserting a LoaderOptions parameter.
     */
    private static class YamlConstructorTransformer extends ModifierVisitor<Void> {
        private int transformationCount = 0;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a Yaml constructor call
            if (isYamlConstructorCall(n)) {
                // Check if it has exactly 4 parameters (old signature)
                if (n.getArguments().size() == 4) {
                    // Transform to new signature by inserting LoaderOptions between 3rd and 4th params
                    List<Expression> arguments = n.getArguments();
                    
                    // Create new LoaderOptions() expression
                    ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                    loaderOptions.setType(new ClassOrInterfaceType(null, LOADER_OPTIONS_CLASS_NAME));
                    
                    // Insert LoaderOptions as the 4th parameter (index 3)
                    arguments.add(3, loaderOptions);
                    
                    transformationCount++;
                    System.out.println("  - Transformed Yaml constructor at line " + 
                                     n.getRange().map(r -> r.begin.line).orElse(-1));
                }
            }
            
            return super.visit(n, arg);
        }
        
        /**
         * Checks if the given ObjectCreationExpr is a Yaml constructor call.
         */
        private boolean isYamlConstructorCall(ObjectCreationExpr expr) {
            return expr.getType().asString().equals("Yaml") ||
                   expr.getType().asString().equals(YAML_CLASS_NAME) ||
                   (expr.getScope().isPresent() && 
                    expr.getScope().get().toString().contains("snakeyaml"));
        }
    }
}