package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        SourceRoot sourceRoot = new SourceRoot(Paths.get(args[0]));
        
        // Parse all Java files in the directory
        sourceRoot.parse("", (localPath, absolutePath, result) -> {
            if (result.isSuccessful()) {
                CompilationUnit cu = result.getResult().orElse(null);
                if (cu != null) {
                    TransformVisitor visitor = new TransformVisitor();
                    cu.accept(visitor, null);
                }
            }
            return result;
        });
        
        sourceRoot.saveAll();
    }
    
    static class TransformVisitor extends ModifierVisitor<Void> {
        @Override
        public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
            // Check if this import is from javax.interceptor package
            String name = importDecl.getNameAsString();
            if (name.startsWith("javax.interceptor")) {
                // Replace with jakarta.interceptor
                String newName = name.replace("javax.interceptor", "jakarta.interceptor");
                importDecl.setName(newName);
            }
            return importDecl;
        }
    }
}