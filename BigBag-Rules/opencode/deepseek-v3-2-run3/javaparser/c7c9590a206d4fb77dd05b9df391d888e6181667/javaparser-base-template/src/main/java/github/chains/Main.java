package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <oldFQN> <newFQN>");
            System.err.println("Example: java github.chains.Main /path/to/src org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext org.apache.maven.doxia.siterenderer.RenderingContext");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String oldFQN = args[1];
        String newFQN = args[2];
        
        try {
            transformProject(sourceDir, oldFQN, newFQN);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir, String oldFQN, String newFQN) throws FileNotFoundException {
        File rootDir = new File(sourceDir);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        List<File> javaFiles = findJavaFiles(rootDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        PackageRelocationVisitor visitor = new PackageRelocationVisitor(oldFQN, newFQN);
        
        int modifiedFiles = 0;
        for (File javaFile : javaFiles) {
            try {
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse: " + javaFile)
                );
                
                visitor.visit(cu, null);
                // Write modified content back to file
                try (java.io.FileWriter writer = new java.io.FileWriter(javaFile)) {
                    writer.write(cu.toString());
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile.getPath());
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile.getPath() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(dir, javaFiles);
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
    
    static class PackageRelocationVisitor extends ModifierVisitor<Void> {
        private final String oldFQN;
        private final String newFQN;
        private final String oldClassName;
        private final String newClassName;
        
        public PackageRelocationVisitor(String oldFQN, String newFQN) {
            this.oldFQN = oldFQN;
            this.newFQN = newFQN;
            this.oldClassName = extractClassName(oldFQN);
            this.newClassName = extractClassName(newFQN);
        }
        
        private String extractClassName(String fqn) {
            int lastDot = fqn.lastIndexOf('.');
            return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            if (importName.equals(oldFQN)) {
                importDecl.setName(newFQN);
                return importDecl;
            }
            
            if (importName.equals(oldFQN + ".*")) {
                importDecl.setName(newFQN + ".*");
                return importDecl;
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(Name name, Void arg) {
            String nameStr = name.asString();
            
            if (nameStr.equals(oldFQN)) {
                name.setQualifier(new Name(newFQN.substring(0, newFQN.lastIndexOf('.'))));
                name.setIdentifier(newClassName);
                return name;
            }
            
            if (nameStr.endsWith("." + oldClassName) && !nameStr.equals(newFQN)) {
                String prefix = nameStr.substring(0, nameStr.length() - oldClassName.length() - 1);
                if (prefix.equals(oldFQN.substring(0, oldFQN.lastIndexOf('.')))) {
                    name.setQualifier(new Name(newFQN.substring(0, newFQN.lastIndexOf('.'))));
name.setIdentifier(newClassName);
                    return name;
                }
            }
            
            return (Node) super.visit(name, arg);
        }
    }
}