package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Generic transformation rule for zip4j 2.x migration:
 * Changes imports from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
 * 
 * This transformation handles:
 * 1. Import statements: import net.lingala.zip4j.core.ZipFile -> import net.lingala.zip4j.ZipFile
 * 2. Type references in code are handled automatically through import resolution
 */
public class Main {
    
    // Configuration: Old and new fully-qualified class names
    private static final String OLD_FQN = "net.lingala.zip4j.core.ZipFile";
    private static final String NEW_FQN = "net.lingala.zip4j.ZipFile";
    
    // Old and new package names for wildcard imports
    private static final String OLD_PACKAGE = "net.lingala.zip4j.core";
    private static final String NEW_PACKAGE = "net.lingala.zip4j";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming zip4j imports in: " + sourceDir);
        System.out.println("Transformation: " + OLD_FQN + " -> " + NEW_FQN);
        
        try {
            transformDirectory(Paths.get(sourceDir));
            System.out.println("Transformation complete!");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }
    
    private static void transformFile(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            JavaParser parser = new JavaParser();
            Optional<CompilationUnit> cuOpt = parser.parse(content).getResult();
            
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                boolean modified = false;
                
                // Transform import declarations
                List<ImportDeclaration> imports = cu.getImports();
                for (ImportDeclaration importDecl : imports) {
                    String importName = importDecl.getNameAsString();
                    
                    // Check for exact match of old FQN
                    if (importName.equals(OLD_FQN)) {
                        importDecl.setName(NEW_FQN);
                        modified = true;
                        System.out.println("Updated import in " + javaFile + ": " + OLD_FQN + " -> " + NEW_FQN);
                    }
                    
                    // Also handle wildcard imports (though less common)
                    if (importDecl.isAsterisk()) {
                        String packageName = importName;
                        if (packageName.equals(OLD_PACKAGE)) {
                            importDecl.setName(NEW_PACKAGE);
                            modified = true;
                            System.out.println("Updated wildcard import in " + javaFile + ": " + OLD_PACKAGE + ".* -> " + NEW_PACKAGE + ".*");
                        }
                    }
                }
                
                if (modified) {
                    // Write the transformed file back
                    PrinterConfiguration config = new DefaultPrinterConfiguration();
                    DefaultPrettyPrinterVisitor visitor = new DefaultPrettyPrinterVisitor(config);
                    cu.accept(visitor, null);
                    String transformedContent = visitor.toString();
                    
                    Files.writeString(javaFile, transformedContent);
                    System.out.println("Saved changes to: " + javaFile);
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        }
    }
}