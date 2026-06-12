package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<Path> javaFiles = findJavaFiles(sourcePath);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int filesModified = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    filesModified++;
                }
            }
            
            System.out.println("Successfully modified " + filesModified + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path startDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walkFileTree(startDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    javaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                // Skip hidden directories like .git
                if (dir.getFileName().toString().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return javaFiles;
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        boolean[] modified = new boolean[]{false};
        
        // Remove imports of com.gargoylesoftware.htmlunit.ScriptResult
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals("com.gargoylesoftware.htmlunit.ScriptResult")) {
                importsToRemove.add(imp);
                modified[0] = true;
            }
        }
        for (ImportDeclaration imp : importsToRemove) {
            cu.remove(imp);
        }
        
        // Transform ScriptResult usage patterns
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public com.github.javaparser.ast.Node visit(MethodCallExpr n, Void arg) {
                // Check if this is a getJavaScriptResult() call
                if (n.getNameAsString().equals("getJavaScriptResult")) {
                    // Check if the scope is a new ScriptResult(...) expression
                    if (n.getScope().isPresent()) {
                        Expression scope = n.getScope().get();
                        if (scope instanceof ObjectCreationExpr) {
                            ObjectCreationExpr objectCreation = (ObjectCreationExpr) scope;
                            if (objectCreation.getType().getNameAsString().equals("ScriptResult")) {
                                // Get the argument passed to ScriptResult constructor
                                NodeList<Expression> args = objectCreation.getArguments();
                                if (args.size() == 1) {
                                    modified[0] = true;
                                    // Replace the entire MethodCallExpr with the argument
                                    return args.get(0);
                                }
                            }
                        }
                    }
                }
                return (com.github.javaparser.ast.Node) super.visit(n, arg);
            }
            
            @Override
            public com.github.javaparser.ast.Node visit(ObjectCreationExpr n, Void arg) {
                // Check if this is a new ScriptResult(...) expression
                if (n.getType().getNameAsString().equals("ScriptResult")) {
                    modified[0] = true;
                    // For now, we'll handle this in the parent context
                    // The actual replacement happens in visit(MethodCallExpr)
                    return (com.github.javaparser.ast.Node) super.visit(n, arg);
                }
                return (com.github.javaparser.ast.Node) super.visit(n, arg);
            }
        }, null);
        
        if (modified[0]) {
            // Write the modified file back with proper formatting
            String newContent = cu.toString();
            // Fix any formatting issues
            newContent = newContent.replace("public class // TODO:", "// TODO:");
            newContent = newContent.replace("\n// TODO:", "\n// TODO:");
            Files.write(javaFile, newContent.getBytes());
            System.out.println("Modified: " + javaFile);
            return true;
        }
        
        return false;
    }
}