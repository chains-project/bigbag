package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        } catch (IOException e) {
            System.err.println("Error traversing directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + file)
            );
            
            HamcrestConstructorFixer fixer = new HamcrestConstructorFixer();
            fixer.visit(cu, null);
            
            // Write back if changes were made
            if (fixer.changesMade) {
                Files.write(file, cu.toString().getBytes());
                System.out.println("Updated: " + file);
            }
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    private static class HamcrestConstructorFixer extends VoidVisitorAdapter<Void> {
        boolean changesMade = false;
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a StringContains or StringStartsWith constructor call
            String typeName = n.getType().asString();
            boolean isStringContains = "StringContains".equals(typeName) || 
                                      "org.hamcrest.core.StringContains".equals(typeName);
            boolean isStringStartsWith = "StringStartsWith".equals(typeName) || 
                                        "org.hamcrest.core.StringStartsWith".equals(typeName);
            
            if (isStringContains || isStringStartsWith) {
                // Check if we have 2 arguments and first is a boolean literal
                if (n.getArguments().size() == 2) {
                    Expression firstArg = n.getArguments().get(0);
                    // Check if first argument is a boolean literal (true/false)
                    if (firstArg.isBooleanLiteralExpr()) {
                        // Remove the boolean parameter, keep only the string parameter
                        NodeList<Expression> newArgs = new NodeList<>();
                        newArgs.add(n.getArguments().get(1)); // Keep the second argument (string)
                        n.setArguments(newArgs);
                        changesMade = true;
                    }
                }
            }
        }
    }
}