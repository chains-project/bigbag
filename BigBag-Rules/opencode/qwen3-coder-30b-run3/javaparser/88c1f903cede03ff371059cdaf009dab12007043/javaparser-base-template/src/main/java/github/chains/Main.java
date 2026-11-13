package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation to fix breaking changes in zip4j dependency.
 * Specifically handles the removal of net.lingala.zip4j.core.ZipFile class.
 * 
 * Before: import net.lingala.zip4j.core.ZipFile;
 * After:  import net.lingala.zip4j.ZipFile;
 * 
 * Before: new net.lingala.zip4j.core.ZipFile(...)
 * After:  new net.lingala.zip4j.ZipFile(...)
 */
public class Main {
    
    public static void main(String[] args) {
        // Get the source directory from command-line argument or default to current directory
        String sourceDir = args.length > 0 ? args[0] : ".";
        
        try {
            File dir = new File(sourceDir);
            if (!dir.isDirectory()) {
                System.err.println("Source directory does not exist: " + sourceDir);
                System.exit(1);
            }
            
            System.out.println("Processing files in: " + sourceDir);
            
            // Process all Java files recursively
            processJavaFiles(dir);
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFiles(File dir) throws IOException {
        File[] javaFiles = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (javaFiles != null) {
            for (File file : javaFiles) {
                processJavaFile(file);
            }
        }
        
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                processJavaFiles(subDir);
            }
        }
    }
    
    private static void processJavaFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply our transformation visitor
        Zip4jFixVisitor visitor = new Zip4jFixVisitor();
        cu.accept(visitor, null);
        
        // Write back the modified content only if changes were made
        if (visitor.hasChanges()) {
            Files.write(Paths.get(file.getAbsolutePath()), cu.toString().getBytes());
            System.out.println("Updated: " + file.getAbsolutePath());
        }
    }
    
    /**
     * Visitor that transforms zip4j API references from old format to new format
     */
    private static class Zip4jFixVisitor extends ModifierVisitor<Void> {
        private boolean changesMade = false;
        
        public boolean hasChanges() {
            return changesMade;
        }
        
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Check if this is an import statement for the old zip4j.core.ZipFile
            if (n.getNameAsString().equals("net.lingala.zip4j.core.ZipFile")) {
                // Replace with the new zip4j.ZipFile
                n.setName("net.lingala.zip4j.ZipFile");
                changesMade = true;
            }
            
            return super.visit(n, arg);
        }
    }
}
            
            System.out.println("Processing files in: " + sourceDir);
            
            // Process all Java files recursively
            processJavaFiles(dir);
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFiles(File dir) throws IOException {
        File[] javaFiles = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (javaFiles != null) {
            for (File file : javaFiles) {
                processJavaFile(file);
            }
        }
        
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                processJavaFiles(subDir);
            }
        }
    }
    
    private static void processJavaFile(File file) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply our transformation visitor
        Zip4jFixVisitor visitor = new Zip4jFixVisitor();
        cu.accept(visitor, null);
        
        // Write back the modified content only if changes were made
        if (visitor.hasChanges()) {
            Files.write(Paths.get(file.getAbsolutePath()), cu.toString().getBytes());
            System.out.println("Updated: " + file.getAbsolutePath());
        }
    }
    
    /**
     * Visitor that transforms zip4j API references from old format to new format
     */
    private static class Zip4jFixVisitor extends ModifierVisitor<Void> {
        private boolean changesMade = false;
        
        public boolean hasChanges() {
            return changesMade;
        }
        
        @Override
        public Visitable visit(ImportDeclaration n, Void arg) {
            // Check if this is an import statement for the old zip4j.core.ZipFile
            if (n.getNameAsString().equals("net.lingala.zip4j.core.ZipFile")) {
                // Replace with the new zip4j.ZipFile
                n.setName("net.lingala.zip4j.ZipFile");
                changesMade = true;
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            // Handle direct class references like new net.lingala.zip4j.core.ZipFile(...)
            if (n.getNameAsString().equals("ZipFile") && 
                n.getScope().isPresent() && 
                n.getScope().get().asString().equals("core")) {
                
                // Find the qualified name to check if it matches net.lingala.zip4j.core.ZipFile
                if (n.getParentNode().isPresent()) {
                    if (n.getParentNode().get() instanceof Name) {
                        Name fullName = (Name) n.getParentNode().get();
                        if (fullName.asString().equals("net.lingala.zip4j.core.ZipFile")) {
                            // Transform the type reference from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
                            n.setScope(new Name("net.lingala.zip4j"));
                            changesMade = true;
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}
