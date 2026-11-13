package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
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

public class Main {
    
    // Configuration parameters - these can be changed for different dependency migrations
    private static final String OLD_PACKAGE = "net.lingala.zip4j.core";
    private static final String NEW_PACKAGE = "net.lingala.zip4j";
    private static final String CLASS_NAME = "ZipFile";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying transformation to: " + sourceDir);
        System.out.println("Old package: " + OLD_PACKAGE);
        System.out.println("New package: " + NEW_PACKAGE);
        System.out.println("Class name: " + CLASS_NAME);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        return Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static boolean transformFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            
            if (cu == null) {
                return false;
            }
            
            boolean wasModified = false;
            
            // Transform imports
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().equals(OLD_PACKAGE + "." + CLASS_NAME)) {
                    importDecl.setName(new Name(NEW_PACKAGE + "." + CLASS_NAME));
                    wasModified = true;
                    System.out.println("Updated import in " + javaFile + ": " + 
                                     OLD_PACKAGE + "." + CLASS_NAME + " -> " + 
                                     NEW_PACKAGE + "." + CLASS_NAME);
                }
            }
            
            // Transform fully qualified type references
            TypeReferenceTransformer typeTransformer = new TypeReferenceTransformer();
            cu.accept(typeTransformer, null);
            if (typeTransformer.wasModified()) {
                wasModified = true;
            }
            
            if (wasModified) {
                Files.write(javaFile, cu.toString().getBytes());
                return true;
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Error transforming file " + javaFile + ": " + e.getMessage());
            return false;
        }
    }
    
    private static class TypeReferenceTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(Name name, Void arg) {
            String nameStr = name.asString();
            if (nameStr.equals(OLD_PACKAGE + "." + CLASS_NAME)) {
                modified = true;
                return new Name(NEW_PACKAGE + "." + CLASS_NAME);
            }
            return super.visit(name, arg);
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
}