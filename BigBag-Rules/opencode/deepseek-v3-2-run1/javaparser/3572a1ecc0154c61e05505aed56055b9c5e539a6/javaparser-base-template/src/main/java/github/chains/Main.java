package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.Node;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    // Classes that moved from org.apache.thrift.transport to org.apache.thrift.transport.layered
    // NOTE: These constructors also now throw TTransportException in version 0.16.0+
    private static final List<String> MOVED_CLASSES = List.of(
        "TFastFramedTransport",
        "TFramedTransport"
    );
    
    // Old package prefix
    private static final String OLD_PACKAGE = "org.apache.thrift.transport";
    
    // New package prefix  
    private static final String NEW_PACKAGE = "org.apache.thrift.transport.layered";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            int totalImportsUpdated = 0;
            
            for (Path javaFile : javaFiles) {
                FileModificationResult result = processJavaFile(javaFile);
                if (result.modified) {
                    modifiedFiles++;
                    totalImportsUpdated += result.importsUpdated;
                    System.out.println("Updated " + result.importsUpdated + " import(s) in " + javaFile);
                }
            }
            
            System.out.println("\nSummary:");
            System.out.println("Total files processed: " + javaFiles.size());
            System.out.println("Files modified: " + modifiedFiles);
            System.out.println("Total imports updated: " + totalImportsUpdated);
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        Path start = Paths.get(sourceDir);
        if (!Files.exists(start) || !Files.isDirectory(start)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static FileModificationResult processJavaFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        JavaParser parser = new JavaParser();
        
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
            () -> new IOException("Failed to parse: " + javaFile)
        );
        
        ImportUpdaterVisitor visitor = new ImportUpdaterVisitor();
        Node modifiedCu = (Node) visitor.visit(cu, null);
        
        if (visitor.getImportUpdateCount() > 0) {
            // Write modified content back to file
            String modifiedContent = modifiedCu.toString();
            
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(modifiedContent);
            }
            
            return new FileModificationResult(true, visitor.getImportUpdateCount());
        }
        
        return new FileModificationResult(false, 0);
    }
    
    private static class ImportUpdaterVisitor extends ModifierVisitor<Void> {
        private int importUpdateCount = 0;
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this is an import of a class that moved to layered package
            for (String className : MOVED_CLASSES) {
                if (importName.equals(OLD_PACKAGE + "." + className)) {
                    // Update the import to new package
                    importDecl.setName(new Name(NEW_PACKAGE + "." + className));
                    importUpdateCount++;
                    System.out.println("  Updated import: " + importName + " -> " + NEW_PACKAGE + "." + className);
                    break;
                }
            }
            
            // Also handle wildcard imports - these need special handling
            if (importDecl.isAsterisk()) {
                String packageName = importName;
                if (packageName.equals(OLD_PACKAGE)) {
                    // This is a wildcard import of org.apache.thrift.transport.*
                    // We need to check if any of the moved classes are used in the file
                    // For simplicity, we'll just add the new import alongside the old one
                    // The actual fix would require more complex analysis
                    System.out.println("  Warning: Wildcard import detected: " + importName);
                    System.out.println("    May need manual checking for layered transport classes");
                }
            }
            
            return super.visit(importDecl, arg);
        }
        
        public int getImportUpdateCount() {
            return importUpdateCount;
        }
    }
    
    private static class FileModificationResult {
        final boolean modified;
        final int importsUpdated;
        
        FileModificationResult(boolean modified, int importsUpdated) {
            this.modified = modified;
            this.importsUpdated = importsUpdated;
        }
    }
}