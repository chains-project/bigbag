package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.utils.SourceRoot;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        File sourceDirectory = new File(sourceDir);
        
        if (!sourceDirectory.exists() || !sourceDirectory.isDirectory()) {
            System.err.println("Invalid source directory: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Applying JsonBranch transformation to " + sourceDir + "...");
        
        // Process all Java files recursively
        int processedFiles = 0;
        try {
            SourceRoot root = new SourceRoot(sourceDirectory.toPath());
            root.parse("", (localPath, absolutePath) -> {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(absolutePath.toFile());
                    JsonBranchTransformation transformation = new JsonBranchTransformation();
                    cu.accept(transformation, null);
                    return cu;
                } catch (Exception e) {
                    System.err.println("Error processing " + absolutePath + ": " + e.getMessage());
                    return null;
                }
            });
            root.saveAll();
            System.out.println("Transformation completed successfully. Processed " + processedFiles + " files.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class JsonBranchTransformation extends ModifierVisitor<Void> {
        private static final String OLD_IMPORT = "de.gwdg.metadataqa.api.json.JsonBranch";
        private static final String NEW_IMPORT = "de.gwdg.metadataqa.api.json.DataElement";
        
        @Override
        public ImportDeclaration visit(ImportDeclaration importDecl, Void arg) {
            if (importDecl.getNameAsString().equals(OLD_IMPORT)) {
                // Replace the import
                importDecl.setName(NEW_IMPORT);
                System.out.println("Updated import from " + OLD_IMPORT + " to " + NEW_IMPORT);
            }
            return super.visit(importDecl, arg);
        }
    }
}