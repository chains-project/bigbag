package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing directory: " + sourceDir);
        
        // Process all Java files in the directory recursively
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path path) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(path.toFile());
            
            // Apply the Hamcrest fixer
            cu.accept(new HamcrestFixer(), null);
            
            // Save the modified file back
            String content = cu.toString();
            Files.write(path, content.getBytes(StandardCharsets.UTF_8));
            
            System.out.println("Processed: " + path);
        } catch (IOException e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
        }
    }
    
    public static class HamcrestFixer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check if this is a StringContains or StringStartsWith constructor call
            if (n.getType().getNameAsString().equals("StringContains") || 
                n.getType().getNameAsString().equals("StringStartsWith")) {
                
                // Check if it has exactly 2 arguments (the problematic signature)
                if (n.getArguments().size() == 2) {
                    Expression firstArg = n.getArguments().get(0);
                    Expression secondArg = n.getArguments().get(1);
                    
                    // Check if first argument is a boolean literal
                    if (firstArg instanceof BooleanLiteralExpr) {
                        BooleanLiteralExpr boolExpr = (BooleanLiteralExpr) firstArg;
                        
                        // If first argument is true, replace with containsString or startsWith
                        // If first argument is false, replace with not(containsString or startsWith)
                        if (boolExpr.getValue()) {
                            // Replace with CoreMatchers.containsString or CoreMatchers.startsWith
                            if (n.getType().getNameAsString().equals("StringContains")) {
                                NodeList<Expression> newArgs = new NodeList<>();
                                newArgs.add(secondArg.clone());
                                Expression replacement = new MethodCallExpr(
                                    new NameExpr("CoreMatchers"),
                                    "containsString",
                                    newArgs
                                );
                                n.replace(replacement);
                            } else { // StringStartsWith
                                NodeList<Expression> newArgs = new NodeList<>();
                                newArgs.add(secondArg.clone());
                                Expression replacement = new MethodCallExpr(
                                    new NameExpr("CoreMatchers"),
                                    "startsWith",
                                    newArgs
                                );
                                n.replace(replacement);
                            }
                        } else { // if first argument is false
                            // Replace with CoreMatchers.not(CoreMatchers.containsString/startsWith)
                            if (n.getType().getNameAsString().equals("StringContains")) {
                                NodeList<Expression> innerArgs = new NodeList<>();
                                innerArgs.add(secondArg.clone());
                                Expression innerCall = new MethodCallExpr(
                                    new NameExpr("CoreMatchers"),
                                    "containsString",
                                    innerArgs
                                );
                                
                                NodeList<Expression> outerArgs = new NodeList<>();
                                outerArgs.add(innerCall);
                                Expression replacement = new MethodCallExpr(
                                    new NameExpr("CoreMatchers"),
                                    "not",
                                    outerArgs
                                );
                                n.replace(replacement);
                            } else { // StringStartsWith
                                NodeList<Expression> innerArgs = new NodeList<>();
                                innerArgs.add(secondArg.clone());
                                Expression innerCall = new MethodCallExpr(
                                    new NameExpr("CoreMatchers"),
                                    "startsWith",
                                    innerArgs
                                );
                                
                                NodeList<Expression> outerArgs = new NodeList<>();
                                outerArgs.add(innerCall);
                                Expression replacement = new MethodCallExpr(
                                    new NameExpr("CoreMatchers"),
                                    "not",
                                    outerArgs
                                );
                                n.replace(replacement);
                            }
                        }
                    }
                }
            }
        }
    }
}
