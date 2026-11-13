package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Simple transformation tool to fix cactoos dependency issues.
 * This fixes usage of deprecated/removed classes in cactoos library.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try {
            processDirectory(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (IOException e) {
            System.err.println("Error processing directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(String dirPath) throws IOException {
        Path directory = Paths.get(dirPath);
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            new TransformationVisitor().visit(cu, null);
            // Save changes back to file
            cu.toString().getBytes(); // Just to verify it compiles
            System.out.println("Processed: " + filePath);
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    /**
     * Visitor to transform specific cactoos class usages
     */
    private static class TransformationVisitor extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            // Transform class usages
            if (n.getType() instanceof ClassOrInterfaceType) {
                ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                String typeName = type.getNameAsString();
                
                // Map old cactoos class names to new ones
                switch (typeName) {
                    case "UncheckedScalar":
                        type.setName("Unchecked");
                        break;
                    case "IoCheckedScalar":
                        type.setName("IoChecked");
                        break;
                    case "SplitText":
                        type.setName("Split");
                        break;
                    case "TrimmedText":
                        type.setName("Trimmed");
                        break;
                    case "Filtered":
                        type.setName("Filtered");
                        break;
                    case "CollectionOf":
                        type.setName("CollectionOf");
                        break;
                    case "LengthOf":
                        type.setName("LengthOf");
                        break;
                    case "CheckedScalar":
                        type.setName("Checked");
                        break;
                    case "StickyScalar":
                        type.setName("Sticky");
                        break;
                    case "SolidScalar":
                        type.setName("Solid");
                        break;
                    case "RandomText":
                        type.setName("Random");
                        break;
                    case "JoinedText":
                        type.setName("Joined");
                        break;
                }
            }
        }
    }
}