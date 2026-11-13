package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse " + javaFile)
            );
            
            boolean[] modified = {false};
            
            // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(ImportDeclaration id, Void arg) {
                    String importName = id.getNameAsString();
                    if (importName.equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                        modified[0] = true;
                        return null; // Remove this import
                    }
                    return (Node) super.visit(id, arg);
                }
            }, null);
            
            // Replace new ScriptResult(expr) with expr
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(ObjectCreationExpr expr, Void arg) {
                    // Check if this is new ScriptResult(...)
                    if (expr.getType().asString().equals("ScriptResult")) {
                        // Get the argument expression
                        NodeList<Expression> arguments = expr.getArguments();
                        if (arguments.size() == 1) {
                            modified[0] = true;
                            return arguments.get(0); // Replace with the argument
                        }
                    }
                    return (Node) super.visit(expr, arg);
                }
            }, null);
            
            // Replace scriptResult.getJavaScriptResult() with scriptResult
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Node visit(MethodCallExpr expr, Void arg) {
                    // Check if this is getJavaScriptResult() call
                    if (expr.getNameAsString().equals("getJavaScriptResult")) {
                        // Check if the scope is a ScriptResult variable or expression
                        // We'll replace the entire method call with its scope
                        if (expr.getScope().isPresent()) {
                            modified[0] = true;
                            return expr.getScope().get(); // Replace method call with its scope
                        }
                    }
                    return (Node) super.visit(expr, arg);
                }
            }, null);
            
            if (modified[0]) {
                // Write the transformed file back
                DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
                String transformedCode = printer.print(cu);
                Files.write(javaFile, transformedCode.getBytes());
                System.out.println("Transformed: " + javaFile);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}