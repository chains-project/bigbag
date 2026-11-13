package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <sourceDirectory>");
            System.err.println("  sourceDirectory: Root directory containing Java files to transform");
            System.err.println("Example: java Main /path/to/project/src");
            System.err.println("This will fix StringContains and StringStartsWith constructor calls");
            System.exit(1);
        }
        
String sourceDir = args[0];
        
        try {
            List<File> javaFiles = findJavaFiles(new File(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int totalTransformations = 0;
            for (File file : javaFiles) {
                int transformations = transformFile(file);
                totalTransformations += transformations;
                if (transformations > 0) {
                    System.out.println("Transformed " + transformations + " constructors in " + file.getPath());
                }
            }
            
            System.out.println("Total transformations: " + totalTransformations);
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }
    
    private static int transformFile(File file) throws FileNotFoundException, IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
        
        ConstructorTransformer transformer = new ConstructorTransformer();
        transformer.visit(cu, null);
        
if (transformer.getTransformationCount() > 0) {
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                String transformedCode = printer.print(cu);
                Files.write(Paths.get(file.getPath()), transformedCode.getBytes());
            }
        
        return transformer.getTransformationCount();
    }
    
    private static class ConstructorTransformer extends VoidVisitorAdapter<Void> {
        private int transformationCount = 0;
        
        public ConstructorTransformer() {
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            // Check if this is a constructor call for StringContains or StringStartsWith
            String className = expr.getType().asString();
            if (className.equals("StringContains") || className.equals("StringStartsWith")) {
                // Check if it has exactly two arguments (boolean + string pattern)
                if (expr.getArguments().size() == 2) {
                    // Check if first argument is a boolean literal
                    if (expr.getArgument(0).isBooleanLiteralExpr()) {
                        // Remove the boolean parameter, keep only the string
                        com.github.javaparser.ast.expr.Expression stringArg = expr.getArgument(1);
                        expr.getArguments().clear();
                        expr.addArgument(stringArg);
                        transformationCount++;
                        System.out.println("Transformed " + className + " constructor at line " + expr.getBegin().get().line);
                    }
                }
            }
        }
    }
}