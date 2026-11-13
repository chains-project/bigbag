package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);

        System.out.println("Found " + javaFiles.size() + " Java files to process");

        int transformedFiles = 0;
        int transformedCalls = 0;

        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);

                if (cu != null) {
                    HamcrestTransformer transformer = new HamcrestTransformer();
                    transformer.visit(cu, null);

                    if (transformer.modified) {
                        // Write back the modified file
                        Files.write(javaFile, cu.toString().getBytes());
                        transformedFiles++;
                        transformedCalls += transformer.modifiedCount;
                        System.out.println("Transformed " + javaFile);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }

        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Constructor calls transformed: " + transformedCalls);
    }

    private static class HamcrestTransformer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        private int modifiedCount = 0;

        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);

            String typeName = expr.getType().asString();
            
            // Check for StringContains or StringStartsWith constructors with 2 arguments
            if (("StringContains".equals(typeName) || "StringStartsWith".equals(typeName)) 
                && expr.getArguments().size() == 2) {
                
                Expression firstArg = expr.getArgument(0);
                Expression secondArg = expr.getArgument(1);
                
                // Check if first argument is a boolean literal
                if (firstArg.isBooleanLiteralExpr()) {
                    // Remove the first boolean argument
                    expr.getArguments().remove(0);
                    modified = true;
                    modifiedCount++;
                    System.out.println("  - Removed boolean parameter from " + typeName + " constructor at line " + expr.getBegin().map(p -> p.line).orElse(-1));
                }
            }
        }
    }
}