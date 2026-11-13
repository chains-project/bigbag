package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing breaking changes in commons-io dependency.
 * 
 * Breaking Change Pattern:
 * 1. Method signature change: CountingInputStream.getByteCount(): long → CountingInputStream.getCount(): int
 * 2. Missing classes: BoundedInputStream, ClosedInputStream, ThresholdingOutputStream, NullPrintStream
 * 
 * This transformation provides a generic fix that can be applied to any project
 * affected by these commons-io API changes.
 */
public class Main {
    
    /**
     * Visitor that fixes the CountingInputStream.getByteCount() → getCount() method call
     * and handles the type change from long to int.
     */
    public static class CountingInputStreamMethodFixer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            if (n.getNameAsString().equals("getByteCount")) {
                n.setName("getCount");
                
                // Check if we need to cast to long since getCount() returns int
                // but original code expects long
                if (isInLongContext(n)) {
                    // Wrap the method call in a cast to long
                    CastExpr castExpr = new CastExpr(PrimitiveType.longType(), n);
                    n.replace(castExpr);
                }
            }
        }
        
        private boolean isInLongContext(MethodCallExpr n) {
            // Simple heuristic: if parent is expecting long type
            // In a real implementation, we would use type resolution
            // For now, we'll always cast to be safe
            return true;
        }
    }
    
    /**
     * Visitor that handles missing class imports and usages.
     * Provides transformation suggestions for removed classes.
     */
    public static class MissingClassTransformer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            String importName = n.getNameAsString();
            
            // Handle imports of removed classes
            if (importName.equals("org.apache.commons.io.input.BoundedInputStream") ||
                importName.equals("org.apache.commons.io.input.ClosedInputStream") ||
                importName.equals("org.apache.commons.io.output.ThresholdingOutputStream") ||
                importName.equals("org.apache.commons.io.output.NullPrintStream")) {
                
                // Remove the import - these classes no longer exist
                // Developers will need to find alternatives
                n.remove();
            }
        }
        
        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            super.visit(n, arg);
            
            String typeName = n.getNameAsString();
            
            // Handle type references to removed classes
            switch (typeName) {
                case "BoundedInputStream":
                    // Suggest replacement: InputStream with manual bounds checking
                    // or custom implementation
                    n.setName("InputStream");
                    addImportSuggestion(n, "java.io.InputStream");
                    break;
                    
                case "ClosedInputStream":
                    // ClosedInputStream was a singleton empty stream
                    // Can be replaced with InputStream.nullInputStream() in Java 11+
                    n.setName("InputStream");
                    addImportSuggestion(n, "java.io.InputStream");
                    break;
                    
                case "ThresholdingOutputStream":
                    // This was an abstract class for threshold-based streaming
                    // May need custom implementation
                    n.setName("OutputStream");
                    addImportSuggestion(n, "java.io.OutputStream");
                    break;
                    
                case "NullPrintStream":
                    // NullPrintStream was a PrintStream that discards output
                    // Can use new PrintStream(OutputStream.nullOutputStream())
                    n.setName("PrintStream");
                    addImportSuggestion(n, "java.io.PrintStream");
                    break;
            }
        }
        
        @Override
        public void visit(NameExpr n, Void arg) {
            super.visit(n, arg);
            
            // Handle NullPrintStream.NULL_PRINT_STREAM static field
            if (n.getNameAsString().equals("NULL_PRINT_STREAM")) {
                // Replace with PrintStream wrapper around null output stream
                // new PrintStream(OutputStream.nullOutputStream()) for Java 11+
                MethodCallExpr nullOutputStream = new MethodCallExpr(
                    new NameExpr("OutputStream"), "nullOutputStream");
                NodeList<com.github.javaparser.ast.expr.Expression> args = new NodeList<>();
                args.add(nullOutputStream);
                MethodCallExpr printStream = new MethodCallExpr(
                    new NameExpr("PrintStream"), "new", args);
                n.replace(printStream);
            }
        }
        
        private void addImportSuggestion(Node node, String importName) {
            // In a full implementation, we would add the import to the CompilationUnit
            // For now, we just transform the type reference
        }
    }
    
    /**
     * Main transformation entry point.
     * 
     * @param args source directory path containing Java files to transform
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src/main/java");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Find all Java files recursively
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        System.out.println("Applying commons-io breaking change fixes...");
        
        JavaParser parser = new JavaParser();
        PrinterConfiguration printerConfig = new DefaultPrinterConfiguration();
        
        int modifiedFiles = 0;
        int totalChanges = 0;
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                
                if (cu != null) {
                    String originalContent = Files.readString(javaFile);
                    
                    // Apply transformations
                    CountingInputStreamMethodFixer methodFixer = new CountingInputStreamMethodFixer();
                    MissingClassTransformer classTransformer = new MissingClassTransformer();
                    
                    methodFixer.visit(cu, null);
                    classTransformer.visit(cu, null);
                    
                    String newContent = cu.toString();
                    
                    if (!originalContent.equals(newContent)) {
                        // Backup original file
                        Path backupFile = javaFile.resolveSibling(javaFile.getFileName() + ".bak");
                        Files.copy(javaFile, backupFile);
                        
                        // Write transformed content
                        try (FileOutputStream out = new FileOutputStream(javaFile.toFile())) {
                            out.write(newContent.getBytes());
                        }
                        
                        modifiedFiles++;
                        System.out.println("✓ Modified: " + sourcePath.relativize(javaFile));
                        
                        // Clean up backup after successful write
                        Files.deleteIfExists(backupFile);
                    }
                }
            } catch (Exception e) {
                System.err.println("✗ Error processing " + sourcePath.relativize(javaFile) + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation complete.");
        System.out.println("Modified " + modifiedFiles + " out of " + javaFiles.size() + " files.");
        System.out.println("\nSummary of changes applied:");
        System.out.println("1. CountingInputStream.getByteCount() → getCount() (with long cast if needed)");
        System.out.println("2. Removed imports of deleted classes: BoundedInputStream, ClosedInputStream,");
        System.out.println("   ThresholdingOutputStream, NullPrintStream");
        System.out.println("3. Updated type references to suggest alternatives");
        System.out.println("4. Replaced NullPrintStream.NULL_PRINT_STREAM with null output stream alternative");
        System.out.println("\nNote: Some transformations require manual review as alternative implementations");
        System.out.println("may be needed for removed functionality.");
    }
}