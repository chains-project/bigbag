package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        processDirectory(new File(sourceDir));
    }
    
    private static void processDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Invalid directory: " + dir.getAbsolutePath());
            return;
        }
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (files != null) {
            for (File file : files) {
                processJavaFile(file);
            }
        }
        
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                processDirectory(subdir);
            }
        }
    }
    
    private static void processJavaFile(File file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            
            // Apply the transformation to replace ScriptResult usage
            ScriptResultReplacementVisitor visitor = new ScriptResultReplacementVisitor();
            cu.accept(visitor, null);
            
            // Save the modified file
            Files.write(Paths.get(file.getAbsolutePath()), cu.toString().getBytes());
            
        } catch (IOException e) {
            System.err.println("Error processing file: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }
    
    private static class ScriptResultReplacementVisitor extends ModifierVisitor<Void> {
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Look for new ScriptResult(...) constructor calls
            if (n.getType().getNameAsString().equals("ScriptResult")) {
                // For chained expressions like new ScriptResult(result).getJavaScriptResult()
                // We'll handle this in the MethodCallExpr visitor
                
                // For now just return the node unchanged
                return super.visit(n, arg);
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Replace chained calls like: new ScriptResult(result).getJavaScriptResult()
            if (n.getNameAsString().equals("getJavaScriptResult") && 
                n.getScope().isPresent() &&
                n.getScope().get() instanceof ObjectCreationExpr) {
                
                ObjectCreationExpr creationExpr = (ObjectCreationExpr) n.getScope().get();
                if (creationExpr.getType().getNameAsString().equals("ScriptResult")) {
                    // Return just the argument to ScriptResult constructor
                    NodeList<Expression> args = creationExpr.getArguments();
                    if (args.size() > 0) {
                        return args.get(0);
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}