package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Generic transformation rule for snakeyaml 1.32 breaking changes.
 * 
 * Breaking change: org.yaml.snakeyaml.representer.Representer.getProperties(Class)
 * no longer throws IntrospectionException in snakeyaml 1.32.
 * 
 * This transformation finds all classes extending org.yaml.snakeyaml.representer.Representer
 * and removes "throws IntrospectionException" from all methods.
 * 
 * Usage: java Main <source-directory>
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Applies snakeyaml 1.32 compatibility transformation to all Java files in directory");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying snakeyaml 1.32 compatibility transformation to: " + sourceDir);
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> processFile(p));
        System.out.println("Transformation complete");
    }
    
    private static void processFile(Path filePath) {
        try {
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(filePath);
            
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                System.err.println("Failed to parse: " + filePath);
                return;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            final boolean[] extendsRepresenter = {false};
            
            // Check if this class extends org.yaml.snakeyaml.representer.Representer
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ClassOrInterfaceDeclaration c, Void arg) {
                    super.visit(c, arg);
                    if (c.getExtendedTypes().isEmpty()) {
                        return;
                    }
                    
                    for (ClassOrInterfaceType ext : c.getExtendedTypes()) {
                        if (ext.getNameAsString().equals("Representer")) {
                            // Check if it's org.yaml.snakeyaml.representer.Representer
                            // by looking at imports
                            for (ImportDeclaration imp : cu.getImports()) {
                                if (imp.getNameAsString().equals("org.yaml.snakeyaml.representer.Representer") ||
                                    imp.getNameAsString().endsWith(".Representer")) {
                                    // This class extends Representer
                                    extendsRepresenter[0] = true;
                                    transformClass(cu, c);
                                    break;
                                }
                            }
                        }
                    }
                }
            }, null);
            
            if (extendsRepresenter[0]) {
                // Write back the transformed file
                Files.write(filePath, cu.toString().getBytes());
                System.out.println("  Processed: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    private static void transformClass(CompilationUnit cu, ClassOrInterfaceDeclaration classDecl) {
        // Find all method declarations in this class
        classDecl.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration md, Void arg) {
                super.visit(md, arg);
                
                // Remove IntrospectionException from all methods in Representer subclasses
                // because super.getProperties() no longer throws it in snakeyaml 1.32
                md.getThrownExceptions().removeIf(expr ->
                    expr.toString().equals("IntrospectionException") ||
                    expr.toString().equals("java.beans.IntrospectionException"));
            }
        }, null);
    }
}