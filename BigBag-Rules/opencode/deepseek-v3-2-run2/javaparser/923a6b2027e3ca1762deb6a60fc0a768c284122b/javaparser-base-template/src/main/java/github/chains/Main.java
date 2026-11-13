package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < Pointers.SOURCE_DIR) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[Pointers.SOURCE_DIR];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws FileNotFoundException, IOException {
        Path rootPath = Paths.get(sourceDir);
        List<File> javaFiles = findJavaFiles(rootPath.toFile());
        
        for (File javaFile : javaFiles) {
            System.out.println("Processing: " + javaFile.getAbsolutePath());
            CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElseThrow();
            
            HamcrestImportTransformer transformer = new HamcrestImportTransformer();
            cu.accept(transformer, null);
            
            if (transformer.isModified()) {
                // Write back the modified file
                writeFile(javaFile, cu);
                System.out.println("  -> File modified");
            }
        }
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }
    
    private static void writeFile(File file, CompilationUnit cu) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(cu.toString());
        }
    }
    
    static class HamcrestImportTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import is from hamcrest-library deprecated packages
            if (importName.startsWith("org.hamcrest.")) {
                // Handle specific class transformations
                String transformedImport = transformHamcrestImport(importName);
                
                if (!transformedImport.equals(importName)) {
                    System.out.println("  Transforming import: " + importName + " -> " + transformedImport);
                    importDecl.setName(new Name(transformedImport));
                    modified = true;
                }
            }
            
            return super.visit(importDecl, arg);
        }
        
        private String transformHamcrestImport(String importName) {
            // Generic transformation rules for Hamcrest 2.2 breaking changes
            // These rules handle the migration from hamcrest-library to hamcrest
            
            // Map of old imports to new imports for Hamcrest 2.2
            // This should be parameterized in a real implementation
            
            // Rule 1: Classes from org.hamcrest.core.* - many moved to org.hamcrest.*
            if (importName.startsWith("org.hamcrest.core.")) {
                String className = importName.substring("org.hamcrest.core.".length());
                // Check if this is a class that should be transformed
                if (shouldTransformCoreClass(className)) {
                    return "org.hamcrest." + className;
                }
            }
            
            // Rule 2: Specific class transformations
            // CustomMatcher was deprecated in favor of TypeSafeMatcher or BaseMatcher
            if (importName.equals("org.hamcrest.CustomMatcher")) {
                System.out.println("  WARNING: CustomMatcher is deprecated. Consider using TypeSafeMatcher or BaseMatcher.");
                // For backward compatibility, we keep it as is
                return importName;
            }
            
            // Rule 3: Handle matcher factory methods that might have moved
            // In Hamcrest 2.2, many matchers moved from Matchers class to separate classes
            
            return importName;
        }
        
        private boolean shouldTransformCoreClass(String className) {
            // List of classes that moved from org.hamcrest.core to org.hamcrest
            // This should be configurable
            String[] movedClasses = {
                "IsAnything", "IsEqual", "IsInstanceOf", "IsNot", "IsNull", "IsSame"
            };
            
            for (String movedClass : movedClasses) {
                if (className.equals(movedClass) || className.startsWith(movedClass + "<")) {
                    return true;
                }
            }
            return false;
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    // Constants for argument indices
    private static class Pointers {
        static final int SOURCE_DIR = 0;
    }
}