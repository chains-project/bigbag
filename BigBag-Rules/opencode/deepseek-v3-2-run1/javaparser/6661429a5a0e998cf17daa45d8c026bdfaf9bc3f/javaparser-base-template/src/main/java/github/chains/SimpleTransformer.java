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
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class SimpleTransformer {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java SimpleTransformer <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming files in: " + sourceDir);
        transformDirectory(new File(sourceDir));
    }
    
    private static void transformDirectory(File directory) throws Exception {
        List<File> javaFiles = findJavaFiles(directory);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (File file : javaFiles) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + file)
                );
                
                boolean modified = transformCompilationUnit(cu);
                
                if (modified) {
                    System.out.println("  - Transforming: " + file.getAbsolutePath());
                    try (FileWriter writer = new FileWriter(file)) {
                        writer.write(cu.toString());
                    }
                    transformedFiles++;
                }
            } catch (Exception e) {
                System.err.println("Error processing " + file + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformed " + transformedFiles + " files");
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean[] modified = {false};
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration id, Void arg) {
                if (id.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                    modified[0] = true;
                    return null; // Remove import
                }
                return super.visit(id, arg);
            }
            
            @Override
            public Node visit(ObjectCreationExpr expr, Void arg) {
                Node visited = (Node) super.visit(expr, arg);
                if (!(visited instanceof ObjectCreationExpr)) {
                    return visited;
                }
                ObjectCreationExpr visitedExpr = (ObjectCreationExpr) visited;
                
                if (visitedExpr.getType().asString().equals("ScriptResult")) {
                    NodeList<Expression> arguments = visitedExpr.getArguments();
                    if (arguments.size() == 1) {
                        // Check if parent is method call
                        if (visitedExpr.getParentNode().isPresent()) {
                            Node parent = visitedExpr.getParentNode().get();
                            if (parent instanceof MethodCallExpr) {
                                MethodCallExpr methodCall = (MethodCallExpr) parent;
                                if (methodCall.getNameAsString().equals("getJavaScriptResult")) {
                                    // Let method call visitor handle it
                                    return visitedExpr;
                                }
                            }
                        }
                        // Replace new ScriptResult(result) with result
                        modified[0] = true;
                        return arguments.get(0);
                    }
                }
                return visitedExpr;
            }
            
            @Override
            public Node visit(MethodCallExpr expr, Void arg) {
                Node visited = (Node) super.visit(expr, arg);
                if (!(visited instanceof MethodCallExpr)) {
                    return visited;
                }
                MethodCallExpr visitedExpr = (MethodCallExpr) visited;
                
                if (visitedExpr.getNameAsString().equals("getJavaScriptResult")) {
                    if (visitedExpr.getScope().isPresent()) {
                        Expression scope = visitedExpr.getScope().get();
                        if (scope instanceof ObjectCreationExpr) {
                            ObjectCreationExpr objectCreation = (ObjectCreationExpr) scope;
                            if (objectCreation.getType().asString().equals("ScriptResult")) {
                                NodeList<Expression> arguments = objectCreation.getArguments();
                                if (arguments.size() == 1) {
                                    modified[0] = true;
                                    // If this method call has a parent that's also a method call,
                                    // we need to handle the chain
                                    if (visitedExpr.getParentNode().isPresent() && 
                                        visitedExpr.getParentNode().get() instanceof MethodCallExpr) {
                                        // We're in a chain like new ScriptResult(result).getJavaScriptResult().toString()
                                        // Return the argument, and let the parent handle the rest
                                        return arguments.get(0);
                                    } else {
                                        // Just new ScriptResult(result).getJavaScriptResult()
                                        return arguments.get(0);
                                    }
                                }
                            }
                        }
                    }
                }
                return visitedExpr;
            }
        }, null);
        
        return modified[0];
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(directory, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File dir, List<File> javaFiles) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
}