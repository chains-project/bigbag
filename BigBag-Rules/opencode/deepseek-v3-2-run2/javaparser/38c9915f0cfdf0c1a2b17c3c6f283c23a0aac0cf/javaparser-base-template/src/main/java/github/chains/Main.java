package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            List<Path> javaFiles = findJavaFiles(sourcePath);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile);
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElse(null);
        
        if (cu == null) {
            return false;
        }
        
        CactoosMigrationVisitor visitor = new CactoosMigrationVisitor();
        boolean modified = false;
        
        // Process imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            String newImport = getReplacementImport(importName);
            if (newImport != null && !newImport.equals(importName)) {
                importDecl.setName(newImport);
                modified = true;
            }
        }
        
        // Process type references in code
        visitor.visit(cu, null);
        
        if (modified) {
            Files.write(filePath, cu.toString().getBytes());
        }
        
        return modified;
    }
    
    private static String getReplacementImport(String importName) {
        // Define the mapping of old imports to new imports
        switch (importName) {
            case "org.cactoos.collection.Filtered":
                return "org.cactoos.iterable.Filtered";
            case "org.cactoos.collection.CollectionOf":
                return "org.cactoos.list.ListOf";
            case "org.cactoos.scalar.CheckedScalar":
                return "org.cactoos.scalar.Checked";
            case "org.cactoos.scalar.UncheckedScalar":
                return "org.cactoos.scalar.Unchecked";
            case "org.cactoos.scalar.IoCheckedScalar":
                return "org.cactoos.scalar.IoChecked";
            case "org.cactoos.scalar.SolidScalar":
                return "org.cactoos.scalar.Solid";
            case "org.cactoos.scalar.StickyScalar":
                return "org.cactoos.scalar.Sticky";
            case "org.cactoos.text.SplitText":
                return "org.cactoos.text.Split";
            case "org.cactoos.text.JoinedText":
                return "org.cactoos.text.Joined";
            case "org.cactoos.text.TrimmedText":
                return "org.cactoos.text.Trimmed";
            case "org.cactoos.iterable.LengthOf":
                return "org.cactoos.scalar.LengthOf";
            default:
                return null;
        }
    }
    
    private static class CactoosMigrationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(Name name, Void arg) {
            String qualifiedName = name.asString();
            String newName = getReplacementType(qualifiedName);
            if (newName != null && !newName.equals(qualifiedName)) {
                return new Name(newName);
            }
            return name;
        }
        
        @Override
        public Node visit(SimpleName name, Void arg) {
            // Handle simple type names in constructor calls and type references
            String simpleName = name.asString();
            String newSimpleName = getReplacementSimpleType(simpleName);
            if (newSimpleName != null && !newSimpleName.equals(simpleName)) {
                return new SimpleName(newSimpleName);
            }
            return name;
        }
        
        private String getReplacementType(String qualifiedName) {
            // Handle fully qualified type names in code
            switch (qualifiedName) {
                case "org.cactoos.collection.Filtered":
                    return "org.cactoos.iterable.Filtered";
                case "org.cactoos.collection.CollectionOf":
                    return "org.cactoos.list.ListOf";
                case "org.cactoos.scalar.CheckedScalar":
                    return "org.cactoos.scalar.Checked";
                case "org.cactoos.scalar.UncheckedScalar":
                    return "org.cactoos.scalar.Unchecked";
                case "org.cactoos.scalar.IoCheckedScalar":
                    return "org.cactoos.scalar.IoChecked";
                case "org.cactoos.scalar.SolidScalar":
                    return "org.cactoos.scalar.Solid";
                case "org.cactoos.scalar.StickyScalar":
                    return "org.cactoos.scalar.Sticky";
                case "org.cactoos.text.SplitText":
                    return "org.cactoos.text.Split";
                case "org.cactoos.text.JoinedText":
                    return "org.cactoos.text.Joined";
                case "org.cactoos.text.TrimmedText":
                    return "org.cactoos.text.Trimmed";
                case "org.cactoos.iterable.LengthOf":
                    return "org.cactoos.scalar.LengthOf";
                default:
                    return null;
            }
        }
        
        private String getReplacementSimpleType(String simpleName) {
            // Handle simple type names (without package)
            switch (simpleName) {
                case "Filtered":
                    return "Filtered"; // Same name, different package
                case "CollectionOf":
                    return "ListOf";
                case "CheckedScalar":
                    return "Checked";
                case "UncheckedScalar":
                    return "Unchecked";
                case "IoCheckedScalar":
                    return "IoChecked";
                case "SolidScalar":
                    return "Solid";
                case "StickyScalar":
                    return "Sticky";
                case "SplitText":
                    return "Split";
                case "JoinedText":
                    return "Joined";
                case "TrimmedText":
                    return "Trimmed";
                case "LengthOf":
                    return "LengthOf"; // Same name, different package
                default:
                    return null;
            }
        }
    }
}