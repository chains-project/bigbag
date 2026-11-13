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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming validation imports in: " + sourceDir);
        
        try {
            int transformedFiles = transformProject(sourceDir);
            System.out.println("Transformation complete. Modified " + transformedFiles + " file(s).");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int transformProject(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath)) {
            throw new IOException("Source directory does not exist: " + sourceDir);
        }
        
        JavaParser parser = new JavaParser();
        int transformedCount = 0;
        
        try (Stream<Path> paths = Files.walk(sourcePath)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                try {
                    CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                    if (cu == null) {
                        continue;
                    }
                    
                    boolean modified = false;
                    
                    // Transform imports
                    for (ImportDeclaration importDecl : cu.getImports()) {
                        String importName = importDecl.getNameAsString();
                        if (importName.startsWith("javax.validation")) {
                            String newImportName = importName.replace("javax.validation", "jakarta.validation");
                            importDecl.setName(newImportName);
                            modified = true;
                        }
                    }
                    
                    // Transform any fully-qualified names in the code
                    ValidationPackageVisitor visitor = new ValidationPackageVisitor();
                    cu.accept(visitor, null);
                    if (visitor.isModified()) {
                        modified = true;
                    }
                    
                    if (modified) {
                        Files.write(path, cu.toString().getBytes());
                        transformedCount++;
                        System.out.println("Modified: " + path);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error processing file: " + path + " - " + e.getMessage());
                }
            }
        }
        
        return transformedCount;
    }
    
    static class ValidationPackageVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(Name name, Void arg) {
            String nameStr = name.asString();
            if (nameStr.startsWith("javax.validation.")) {
                String newName = nameStr.replace("javax.validation.", "jakarta.validation.");
                name.setIdentifier(newName.substring(newName.lastIndexOf('.') + 1));
                if (name.getQualifier().isPresent()) {
                    Name qualifier = name.getQualifier().get();
                    if (qualifier.asString().equals("javax.validation")) {
                        qualifier.setIdentifier("jakarta");
                    } else if (qualifier.asString().startsWith("javax.validation.")) {
                        String qualifierStr = qualifier.asString();
                        String newQualifier = qualifierStr.replace("javax.validation.", "jakarta.validation.");
                        qualifier.setIdentifier(newQualifier);
                    }
                }
                modified = true;
            }
            return super.visit(name, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}