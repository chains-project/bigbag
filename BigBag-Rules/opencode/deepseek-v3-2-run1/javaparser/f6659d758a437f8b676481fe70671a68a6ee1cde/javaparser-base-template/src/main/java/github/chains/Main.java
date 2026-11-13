package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule for fixing breaking changes where:
 * 1. A class was removed in a dependency update
 * 2. The class implemented a functional interface
 * 3. The replacement is a lambda expression
 * 
 * This transformation handles the specific case of:
 * - Old API: org.yaml.snakeyaml.inspector.TrustedTagInspector
 * - New API: TagInspector functional interface (tag -> true)
 * 
 * To adapt for other similar breaking changes:
 * 1. Update OLD_CLASS_FQN constant
 * 2. Update OLD_CLASS_SIMPLE_NAME constant  
 * 3. Update REPLACEMENT_LAMBDA constant
 */
public class Main {
    // Configuration - can be parameterized for different breaking changes
    private static final String OLD_CLASS_FQN = "org.yaml.snakeyaml.inspector.TrustedTagInspector";
    private static final String OLD_CLASS_SIMPLE_NAME = "TrustedTagInspector";
    private static final String REPLACEMENT_LAMBDA = "tag -> true";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Transforms: " + OLD_CLASS_FQN + " -> " + REPLACEMENT_LAMBDA);
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir.toAbsolutePath());
        System.out.println("Transformation: " + OLD_CLASS_FQN + " -> " + REPLACEMENT_LAMBDA);
        
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path file) {
        try {
            CompilationUnit cu = new JavaParser().parse(file).getResult().orElseThrow();
            
            boolean modified = false;
            
            // Remove import of the old class
            List<ImportDeclaration> imports = cu.getImports();
            for (int i = 0; i < imports.size(); i++) {
                ImportDeclaration imp = imports.get(i);
                if (imp.getNameAsString().equals(OLD_CLASS_FQN)) {
                    cu.remove(imp);
                    modified = true;
                    System.out.println("Removed import: " + imp.getNameAsString() + " from " + file);
                }
            }
            
            // Replace constructor calls with lambda
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(ObjectCreationExpr n, Void arg) {
                    if (n.getType().asString().equals(OLD_CLASS_SIMPLE_NAME)) {
                        System.out.println("Replacing new " + OLD_CLASS_SIMPLE_NAME + "() with lambda in " + file);
                        
                        // Create the replacement lambda expression
                        try {
                            LambdaExpr lambda = (LambdaExpr) new JavaParser()
                                .parseExpression(REPLACEMENT_LAMBDA)
                                .getResult()
                                .orElseThrow();
                            return lambda;
                        } catch (Exception e) {
                            System.err.println("Error creating lambda expression: " + e.getMessage());
                            return n; // Return original if we can't create replacement
                        }
                    }
                    return super.visit(n, arg);
                }
            }, null);
            
            if (modified) {
                Files.write(file, cu.toString().getBytes());
                System.out.println("Updated: " + file);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + file + " - " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error parsing file: " + file + " - " + e.getMessage());
        }
    }
}