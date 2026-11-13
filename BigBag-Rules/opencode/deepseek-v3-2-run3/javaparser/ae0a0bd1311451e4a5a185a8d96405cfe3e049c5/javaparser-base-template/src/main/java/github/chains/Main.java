package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Processing source directory: " + sourceDir);
        
        // Find all Java files
        List<Path> javaFiles = Files.walk(sourceDir)
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int modifiedFiles = 0;
        int totalTransformations = 0;
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }
                
                // Find all ObjectCreationExpr (new Something(...))
                List<ObjectCreationExpr> objectCreations = cu.findAll(ObjectCreationExpr.class);
                
                boolean fileModified = false;
                
                for (ObjectCreationExpr creation : objectCreations) {
                    String typeName = creation.getType().toString();
                    // Check if it's creating a Hamcrest matcher object (StringContains, StringStartsWith, etc.)
                    // These are typically in org.hamcrest.core package
                    if (typeName.equals("StringContains") || 
                        typeName.equals("StringStartsWith") ||
                        typeName.equals("StringEndsWith") ||
                        typeName.equals("org.hamcrest.core.StringContains") ||
                        typeName.equals("org.hamcrest.core.StringStartsWith") ||
                        typeName.equals("org.hamcrest.core.StringEndsWith") ||
                        typeName.startsWith("String") && typeName.endsWith("With")) {
                        
                        // Check if it has two arguments (boolean, expression)
                        if (creation.getArguments().size() == 2) {
                            // Check if first argument is boolean
                            if (creation.getArgument(0) instanceof BooleanLiteralExpr) {
                                // Transform: new Matcher(boolean, expression) -> new Matcher(expression)
                                // Remove the boolean argument, keep only the second argument
                                com.github.javaparser.ast.expr.Expression secondArg = creation.getArgument(1);
                                
                                // Create new arguments list with only the second argument
                                creation.getArguments().clear();
                                creation.getArguments().add(secondArg);
                                
                                fileModified = true;
                                totalTransformations++;
                                System.out.println("Transformed " + typeName + " 2-arg: " + javaFile + " line " + creation.getRange().map(r -> r.begin.line).orElse(0));
                            }
                        }
                        // Also check for single argument (no change needed)
                        else if (creation.getArguments().size() == 1) {
                            System.out.println("Already correct " + typeName + " 1-arg: " + javaFile + " line " + creation.getRange().map(r -> r.begin.line).orElse(0));
                        }
                    }
                }
                
                if (fileModified) {
                    // Write the modified file back
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
                    String modifiedCode = printer.print(cu);
                    Files.write(javaFile, modifiedCode.getBytes());
                    modifiedFiles++;
                }
                
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile);
                e.printStackTrace();
            }
        }
        
        System.out.println("Summary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files modified: " + modifiedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
    }
}
