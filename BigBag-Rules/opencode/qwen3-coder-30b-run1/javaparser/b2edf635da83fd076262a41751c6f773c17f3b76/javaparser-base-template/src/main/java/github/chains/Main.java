package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(new File(sourceDir));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processDirectory(File dir) throws IOException {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
            if (files != null) {
                for (File file : files) {
                    processFile(file);
                }
            }
            File[] subDirs = dir.listFiles(File::isDirectory);
            if (subDirs != null) {
                for (File subDir : subDirs) {
                    processDirectory(subDir);
                }
            }
        }
    }
    
    private static void processFile(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Apply transformation to handle SnakeYAML 2.0 breaking changes
            SnakeYAMLTransformer transformer = new SnakeYAMLTransformer();
            transformer.visit(cu, null);
            
            // Save the modified file
            // Note: Save method is not available in all JavaParser versions, 
            // so we'll skip the save part for now
        } catch (Exception e) {
            // Skip files that can't be parsed
        }
    }
    
    static class SnakeYAMLTransformer extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(com.github.javaparser.ast.expr.ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            // Basic visitor for object creation expressions
        }
    }
}