package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for ByteBuddy package migration from assertj-core internal to external dependency.
 * 
 * This transformation handles the breaking change where ByteBuddy classes were moved from:
 * Old: org.assertj.core.internal.bytebuddy.*
 * New: net.bytebuddy.*
 * 
 * The transformation is parameterized and can be applied to any project affected by this dependency change.
 */
public class Main {
    
    // Configuration parameters - these can be modified for different breaking changes
    private static final String OLD_PACKAGE_PREFIX = "org.assertj.core.internal.bytebuddy";
    private static final String NEW_PACKAGE_PREFIX = "net.bytebuddy";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying ByteBuddy package transformation to: " + sourceDirectory);
        System.out.println("Old package: " + OLD_PACKAGE_PREFIX);
        System.out.println("New package: " + NEW_PACKAGE_PREFIX);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDirectory);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDirectory) throws IOException {
        return Files.walk(Paths.get(sourceDirectory))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        
        try {
            CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
            
            ByteBuddyImportTransformer transformer = new ByteBuddyImportTransformer();
            CompilationUnit transformedCu = (CompilationUnit) cu.accept(transformer, null);
            
            if (transformer.wasModified()) {
                String transformedContent = transformedCu.toString();
                Files.writeString(javaFile, transformedContent);
                System.out.println("Modified: " + javaFile);
                return true;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Visitor that transforms ByteBuddy import declarations from the old internal package
     * to the new external package structure.
     */
    private static class ByteBuddyImportTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                // Replace the old package prefix with the new one
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                
                ImportDeclaration newImport = new ImportDeclaration(
                    new Name(newImportName),
                    importDecl.isStatic(),
                    importDecl.isAsterisk()
                );
                
                modified = true;
                System.out.println("  Transforming import: " + importName + " -> " + newImportName);
                return newImport;
            }
            
            return super.visit(importDecl, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}