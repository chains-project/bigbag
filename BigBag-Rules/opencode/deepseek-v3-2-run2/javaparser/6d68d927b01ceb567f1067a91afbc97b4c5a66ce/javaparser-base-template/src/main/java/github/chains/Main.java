package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    
    // Configuration parameters - these can be changed for different dependency updates
    private static final String OLD_IMPORT = "com.gargoylesoftware.htmlunit.ScriptResult";
    private static final String NEW_IMPORT = "org.jenkinsci.test.acceptance.plugins.scriptler.ScriptResult";
    private static final String TARGET_CLASS_NAME = "ScriptResult";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            transformDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
            
            final boolean[] modified = {false};
            
            // Transform imports
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                if (importDecl.getNameAsString().equals(OLD_IMPORT)) {
                    importDecl.setName(NEW_IMPORT);
                    modified[0] = true;
                }
            }
            
            // Transform constructor calls
            cu.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(ObjectCreationExpr n, Void arg) {
                    if (n.getType().asString().equals(TARGET_CLASS_NAME)) {
                        // Check if we need to add .toString() to the argument
                        if (n.getArguments().size() == 1) {
                            Node argument = n.getArguments().get(0);
                            // If argument is not already a String expression, wrap it with .toString()
                            if (!argument.toString().endsWith(".toString()") && 
                                !(argument instanceof MethodCallExpr && 
                                  ((MethodCallExpr) argument).getNameAsString().equals("toString"))) {
                                
                                MethodCallExpr toStringCall = new MethodCallExpr(
                                    (Expression) argument.clone(), "toString", new NodeList<>());
                                n.getArguments().set(0, toStringCall);
                                modified[0] = true;
                            }
                        }
                    }
                    return super.visit(n, arg);
                }
                
                @Override
                public Visitable visit(MethodCallExpr n, Void arg) {
                    // Transform getJavaScriptResult() calls to output(null)
                    if (n.getNameAsString().equals("getJavaScriptResult")) {
                        // Replace with output(null)
                        MethodCallExpr outputCall = new MethodCallExpr(n.getScope().orElse(null), "output", new NodeList<>(new NullLiteralExpr()));
                        modified[0] = true;
                        return outputCall;
                    }
                    return super.visit(n, arg);
                }
            }, null);
            
            if (modified[0]) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Transformed: " + filePath);
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
}