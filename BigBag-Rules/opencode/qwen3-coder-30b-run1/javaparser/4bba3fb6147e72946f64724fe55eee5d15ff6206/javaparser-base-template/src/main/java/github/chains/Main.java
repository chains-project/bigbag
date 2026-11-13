package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }

        String sourceDirectory = args[0];
        try {
            ProjectRoot projectRoot = new ProjectRoot(Paths.get(sourceDirectory));
            List<SourceRoot> sourceRoots = projectRoot.getSourceRoots();
            
            for (SourceRoot sourceRoot : sourceRoots) {
                sourceRoot.parse((path, compilationUnit) -> {
                    // Apply the transformation to fix javax.annotation imports to jakarta.annotation
                    TransformVisitor visitor = new TransformVisitor();
                    compilationUnit.accept(visitor, null);
                    return compilationUnit;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static class TransformVisitor extends ModifierVisitor<Void> {
        @Override
        public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
            // Check if this is a javax.annotation import that needs to be changed
            if (importDecl.getNameAsString().startsWith("javax.annotation")) {
                // Replace javax.annotation with jakarta.annotation
                String newImport = importDecl.getNameAsString().replace("javax.annotation", "jakarta.annotation");
                importDecl.setName(newImport);
            }
            return super.visit(importDecl, arg);
        }
    }
}