package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    
    /**
     * Generic transformation rule for constructor signature changes where
     * a boolean parameter is removed from the beginning.
     * 
     * Transformation: new ClassName(boolean, String) -> new ClassName(String)
     * 
     * @param sourceDir Source directory to transform
     * @param targetDir Target directory for transformed files (can be same as source)
     * @param classNames Fully-qualified class names whose constructors need transformation
     * @throws IOException if file operations fail
     */
    public static void transformConstructorSignatures(
            Path sourceDir, 
            Path targetDir,
            List<String> classNames) throws IOException {
        
        JavaParser javaParser = new JavaParser();
        
        Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFile -> {
                try {
                    CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow();
                    boolean[] modified = new boolean[]{false};
                    
                    cu.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(ObjectCreationExpr expr, Void arg) {
                            super.visit(expr, arg);
                            
                            // Get the type name
                            String typeName = expr.getType().asString();
                            
                            // Check if this is one of the classes we need to transform
                            if (classNames.stream().anyMatch(cn -> cn.endsWith("." + typeName)) 
                                    && expr.getArguments().size() == 2) {
                                
                                // Check if first argument is a boolean literal
                                if (expr.getArguments().get(0).isBooleanLiteralExpr()) {
                                    // Remove the boolean parameter
                                    expr.getArguments().remove(0);
                                    modified[0] = true;
                                }
                            }
                        }
                    }, null);
                    
                    if (modified[0]) {
                        // Write transformed file
                        Path targetFile = targetDir.resolve(sourceDir.relativize(javaFile));
                        Files.createDirectories(targetFile.getParent());
                        try (FileWriter writer = new FileWriter(targetFile.toFile())) {
                            writer.write(cu.toString());
                        }
                        System.out.println("Modified: " + javaFile);
                    }
                    
                } catch (IOException e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            });
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java Main <sourceDir> <targetDir> [class1,class2,...]");
            System.err.println("Example: java Main /path/to/src /path/to/transformed org.hamcrest.core.StringContains,org.hamcrest.core.StringStartsWith");
            return;
        }
        
        Path sourceDir = Paths.get(args[0]);
        Path targetDir = Paths.get(args[1]);
        
        List<String> classNames;
        if (args.length > 2) {
            classNames = Arrays.asList(args[2].split(","));
        } else {
            // Default to Hamcrest StringContains and StringStartsWith
            classNames = Arrays.asList(
                "org.hamcrest.core.StringContains",
                "org.hamcrest.core.StringStartsWith"
            );
        }
        
        try {
            System.out.println("Transforming constructor signatures...");
            System.out.println("Source directory: " + sourceDir);
            System.out.println("Target directory: " + targetDir);
            System.out.println("Affected classes: " + classNames);
            
            transformConstructorSignatures(sourceDir, targetDir, classNames);
            
            System.out.println("Transformation completed successfully!");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}