package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for JavaParser 3.24.0 breaking change:
 * PrettyPrinterConfiguration moved from com.github.javaparser.printer to 
 * com.github.javaparser.printer.configuration
 */
public class Main {
    
    // Configuration for the breaking change
    private static final String OLD_PACKAGE = "com.github.javaparser.printer";
    private static final String OLD_CLASS_NAME = "PrettyPrinterConfiguration";
    private static final String NEW_PACKAGE = "com.github.javaparser.printer.configuration";
    private static final String NEW_CLASS_NAME = "PrettyPrinterConfiguration";
    private static final String OLD_FULL_CLASS = OLD_PACKAGE + "." + OLD_CLASS_NAME;
    private static final String NEW_FULL_CLASS = NEW_PACKAGE + "." + NEW_CLASS_NAME;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformer.jar <source-directory>");
            System.err.println("Example: java -jar transformer.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        System.out.println("Breaking change: " + OLD_FULL_CLASS + " -> " + NEW_FULL_CLASS);
        
        try {
            transformProject(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws IOException {
        List<Path> javaFiles = Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        int transformedFiles = 0;
        int totalChanges = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                JavaParser parser = new JavaParser(new ParserConfiguration());
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
                
                if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                    CompilationUnit cu = parseResult.getResult().get();
                    PrettyPrinterConfigurationTransformer transformer = new PrettyPrinterConfigurationTransformer();
                    
                    // Apply transformation
                    CompilationUnit transformedCu = (CompilationUnit) transformer.visit(cu, null);
                    
                    if (transformer.getChangeCount() > 0) {
                        // Write back the transformed file
                        Files.write(javaFile, transformedCu.toString().getBytes());
                        transformedFiles++;
                        totalChanges += transformer.getChangeCount();
                        System.out.println("Transformed: " + javaFile + " (" + transformer.getChangeCount() + " changes)");
                    }
                } else {
                    System.err.println("Failed to parse file: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("- Files transformed: " + transformedFiles);
        System.out.println("- Total changes: " + totalChanges);
    }
    
    /**
     * Visitor that transforms PrettyPrinterConfiguration references
     */
    private static class PrettyPrinterConfigurationTransformer extends ModifierVisitor<Void> {
        private int changeCount = 0;
        
        public int getChangeCount() {
            return changeCount;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import matches the old class
            if (importName.equals(OLD_FULL_CLASS)) {
                // Replace import with new package
                importDecl.setName(new Name(NEW_FULL_CLASS));
                changeCount++;
                System.out.println("  - Updated import: " + OLD_FULL_CLASS + " -> " + NEW_FULL_CLASS);
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Check if this type reference matches the old class name
            // This handles cases like: PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
            if (typeName.equals(OLD_CLASS_NAME)) {
                // Check if it's not already qualified with the new package
                if (!isQualifiedWithNewPackage(type)) {
                    type.setName(NEW_CLASS_NAME);
                    changeCount++;
                    System.out.println("  - Updated type reference: " + OLD_CLASS_NAME + " -> " + NEW_CLASS_NAME);
                }
            }
            
            return (Node) super.visit(type, arg);
        }
        
        private boolean isQualifiedWithNewPackage(ClassOrInterfaceType type) {
            // Check if this type is already qualified with the new package
            // This prevents double-qualification
            if (type.getScope().isPresent()) {
                String scopeName = type.getScope().get().asString();
                return scopeName.equals(NEW_PACKAGE) || scopeName.startsWith(NEW_PACKAGE + ".");
            }
            return false;
        }
    }
}