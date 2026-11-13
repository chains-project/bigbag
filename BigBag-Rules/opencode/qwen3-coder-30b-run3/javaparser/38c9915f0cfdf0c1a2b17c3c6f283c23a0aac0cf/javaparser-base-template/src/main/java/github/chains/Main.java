package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation for migrating from Cactoos 0.54 to 0.55
 * Fixes breaking changes related to class moves and renames.
 */
public class Main {
    
    public static void main(String[] args) {
        try {
            // Process all Java files in the current directory
            Path sourceDir = Paths.get("/workspace/java-api/src");
            Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Apply transformations to fix Cactoos 0.55 breaking changes
            cu.accept(new CactoosMigrationVisitor(), null);
            
            // Save the modified file
            Files.write(filePath, cu.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor that identifies and fixes Cactoos 0.55 breaking changes
     */
    private static class CactoosMigrationVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration importDecl, Void arg) {
            super.visit(importDecl, arg);
            // Fix import statements for renamed classes
            String importedClass = importDecl.getNameAsString();
            String fixedImport = fixImport(importedClass);
            if (!fixedImport.equals(importedClass)) {
                importDecl.setName(fixedImport);
            }
        }
        
        /**
         * Transform import statements to point to correct package locations
         */
        private String fixImport(String importedClass) {
            // Map of old class names to new class names
            switch (importedClass) {
                case "org.cactoos.collection.Filtered":
                    return "org.cactoos.iterable.Filtered";
                case "org.cactoos.scalar.CheckedScalar":
                    return "org.cactoos.scalar.Checked";
                case "org.cactoos.scalar.UncheckedScalar":
                    return "org.cactoos.scalar.Unchecked";
                case "org.cactoos.text.SplitText":
                    return "org.cactoos.text.Split";
                case "org.cactoos.collection.CollectionOf":
                    return "org.cactoos.iterable.IterableOf";
                case "org.cactoos.scalar.IoCheckedScalar":
                    return "org.cactoos.scalar.IoChecked";
                case "org.cactoos.scalar.SolidScalar":
                    return "org.cactoos.scalar.Solid";
                case "org.cactoos.text.JoinedText":
                    return "org.cactoos.text.Joined";
                case "org.cactoos.scalar.StickyScalar":
                    return "org.cactoos.scalar.Sticky";
                case "org.cactoos.text.TrimmedText":
                    return "org.cactoos.text.Trimmed";
                case "org.cactoos.iterable.LengthOf":
                    return "org.cactoos.text.LengthOf";
                case "org.cactoos.text.RandomText":
                    return "org.cactoos.text.Randomized";
                default:
                    return importedClass;
            }
        }
    }
}