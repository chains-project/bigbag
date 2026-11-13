package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Transforms develop.p2p.lib imports to tokyo.peya.lib");
            System.exit(1);
        }

        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = findJavaFiles(new File(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int totalTransformations = 0;
            
            for (File javaFile : javaFiles) {
                int transformations = transformFile(javaFile);
                if (transformations > 0) {
                    transformedFiles++;
                    totalTransformations += transformations;
                    System.out.println("Transformed " + transformations + " occurrences in " + javaFile.getPath());
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("  Files processed: " + javaFiles.size());
            System.out.println("  Files transformed: " + transformedFiles);
            System.out.println("  Total transformations: " + totalTransformations);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(directory, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    private static int transformFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        PackageRenamerVisitor visitor = new PackageRenamerVisitor();
        cu.accept(visitor, null);
        
        int transformations = visitor.getTransformations();
        if (transformations > 0) {
            // Write back the transformed file
            javaFile.getParentFile().mkdirs();
            javaFile.delete();
            try (java.io.FileWriter writer = new java.io.FileWriter(javaFile)) {
                writer.write(cu.toString());
            } catch (Exception e) {
                throw new RuntimeException("Failed to write " + javaFile, e);
            }
        }
        
        return transformations;
    }
    
    private static class PackageRenamerVisitor extends VoidVisitorAdapter<Void> {
        private int transformations = 0;
        
        public int getTransformations() {
            return transformations;
        }
        
        @Override
        public void visit(ImportDeclaration id, Void arg) {
            super.visit(id, arg);
            
            String importName = id.getNameAsString();
            
            // Check if this import uses the old package
            // Use direct comparison to avoid recursive replacement
            if (importName.startsWith("develop.p2p.lib")) {
                // Handle wildcard imports
                String newImportName;
                if (importName.equals("develop.p2p.lib")) {
                    newImportName = "tokyo.peya.lib";
                } else if (importName.equals("develop.p2p.lib.*")) {
                    newImportName = "tokyo.peya.lib.*";
                } else if (importName.startsWith("develop.p2p.lib.")) {
                    newImportName = "tokyo.peya.lib." + importName.substring("develop.p2p.lib.".length());
                } else {
                    // Should not happen, but fallback
                    newImportName = importName.replace("develop.p2p.lib", "tokyo.peya.lib");
                }
                
                id.setName(newImportName);
                transformations++;
                System.out.println("  [Import] " + importName + " -> " + newImportName);
            }
        }
        
        @Override
        public void visit(Name name, Void arg) {
            super.visit(name, arg);
            
            String nameStr = name.asString();
            
            // Check if this is a fully-qualified name using the old package
            // Use more precise matching
            if (nameStr.equals("develop.p2p.lib")) {
                name.setIdentifier("tokyo.peya.lib");
                transformations++;
                System.out.println("  [Name] " + nameStr + " -> " + "tokyo.peya.lib");
            } else if (nameStr.startsWith("develop.p2p.lib.")) {
                String newNameStr = "tokyo.peya.lib." + nameStr.substring("develop.p2p.lib.".length());
                name.setIdentifier(newNameStr);
                transformations++;
                System.out.println("  [Name] " + nameStr + " -> " + newNameStr);
            }
        }
    }
}