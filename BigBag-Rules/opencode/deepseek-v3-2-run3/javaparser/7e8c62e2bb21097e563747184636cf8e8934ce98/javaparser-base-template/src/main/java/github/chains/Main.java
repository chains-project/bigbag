package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<File> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int[] transformedFiles = {0};
        int[] transformedImports = {0};
        int[] transformedPatterns = {0};
        
        for (File file : javaFiles) {
            try {
                CompilationUnit cu = new JavaParser().parse(file).getResult().orElseThrow(() -> new RuntimeException("Failed to parse " + file));
                boolean[] modified = {false};
                
                // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
                List<ImportDeclaration> importsToRemove = cu.getImports().stream()
                    .filter(imp -> imp.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult"))
                    .collect(Collectors.toList());
                    
                if (!importsToRemove.isEmpty()) {
                    cu.getImports().removeAll(importsToRemove);
                    modified[0] = true;
                    transformedImports[0] += importsToRemove.size();
                    System.out.println("  Removed import: com.gargoylesoftware.htmlunit.ScriptResult from " + file.getName());
                }
                
                // Transform ScriptResult usage patterns
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(ObjectCreationExpr n, Void arg) {
                        // Pattern: new ScriptResult(argument)
                        if (n.getType().toString().equals("ScriptResult") && !n.getArguments().isEmpty()) {
                            Expression argExpr = n.getArguments().get(0);
                            transformedPatterns[0]++;
                            
                            // Replace new ScriptResult(argument) with argument
                            // This handles: new ScriptResult(result)
                            return argExpr;
                        }
                        return super.visit(n, arg);
                    }
                    
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        // Pattern: .getJavaScriptResult()
                        if (n.getName().toString().equals("getJavaScriptResult")) {
                            transformedPatterns[0]++;
                            
                            // Remove .getJavaScriptResult() call
                            // If we have a scope, return it
                            if (n.getScope().isPresent()) {
                                return n.getScope().get();
                            }
                            // Otherwise return the method call expression itself (though unlikely)
                        }
                        return super.visit(n, arg);
                    }
                    
                    @Override
                    public Visitable visit(VariableDeclarationExpr n, Void arg) {
                        // Handle variable declarations like: ScriptResult var = new ScriptResult(result);
                        // This is more complex - we'd need to change the type from ScriptResult to Object
                        // For now, we rely on the ObjectCreationExpr visitor to handle the constructor
                        return super.visit(n, arg);
                    }
                }, null);
                
                if (modified[0]) {
                    // Write back the file
                    Files.write(file.toPath(), cu.toString().getBytes());
                    transformedFiles[0]++;
                }
                
            } catch (FileNotFoundException e) {
                System.err.println("File not found: " + file);
            } catch (Exception e) {
                System.err.println("Error processing " + file + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation summary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles[0]);
        System.out.println("  Imports removed: " + transformedImports[0]);
        System.out.println("  Patterns transformed: " + transformedPatterns[0]);
    }
}