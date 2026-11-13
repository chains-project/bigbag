package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A generic transformation rule for fixing breaking changes in Java dependencies.
 * This tool updates import statements and fully-qualified type references when
 * a class moves from one package to another.
 * 
 * Example usage for JavaParser breaking change:
 *   java Main /path/to/project/src com.github.javaparser.printer.PrettyPrinterConfiguration com.github.javaparser.printer.configuration.PrettyPrinterConfiguration
 */
public class Main {
    
    private final String oldFullName;
    private final String newFullName;
    
    public Main(String oldFullName, String newFullName) {
        this.oldFullName = oldFullName;
        this.newFullName = newFullName;
    }
    
    /**
     * Find all Java files in a directory recursively.
     */
    private List<Path> findJavaFiles(Path rootDir) throws IOException {
        try (Stream<Path> walk = Files.walk(rootDir)) {
            return walk.filter(Files::isRegularFile)
                      .filter(p -> p.toString().endsWith(".java"))
                      .collect(Collectors.toList());
        }
    }
    
    /**
     * Transform a single Java file by updating imports and type references.
     */
    private boolean transformFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        String content = Files.readString(filePath);
        
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        if (cu == null) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        boolean[] modified = new boolean[1];
        
        // 1. Fix import statements
        List<ImportDeclaration> imports = cu.getImports();
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            if (importName.equals(oldFullName)) {
                importDecl.setName(newFullName);
                modified[0] = true;
                System.out.println("  Fixed import: " + oldFullName + " -> " + newFullName);
            }
        }
        
        // 2. Fix fully-qualified type references
        cu.walk(Name.class, name -> {
            String nameStr = name.asString();
            if (nameStr.equals(oldFullName)) {
                name.setIdentifier(newFullName);
                modified[0] = true;
                System.out.println("  Fixed fully-qualified reference: " + oldFullName + " -> " + newFullName);
            }
        });
        
        if (modified[0]) {
            PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
            PrettyPrinter printer = new PrettyPrinter(config);
            String newContent = printer.print(cu);
            Files.writeString(filePath, newContent);
            return true;
        }
        
        return false;
    }
    
    /**
     * Transform all Java files in a directory.
     */
    public int transformDirectory(Path sourceDir) throws IOException {
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int transformedCount = 0;
        for (Path file : javaFiles) {
            System.out.println("Processing: " + sourceDir.relativize(file));
            if (transformFile(file)) {
                transformedCount++;
            }
        }
        
        return transformedCount;
    }
    
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 3) {
            System.err.println("Usage: java Main <source-directory> [<old-full-name> <new-full-name>]");
            System.err.println("\nExamples:");
            System.err.println("  Default (JavaParser breaking change):");
            System.err.println("    java Main /path/to/project/src");
            System.err.println("  Custom breaking change:");
            System.err.println("    java Main /path/to/project/src com.old.package.ClassName com.new.package.ClassName");
            System.err.println("\nSystem properties (alternative):");
            System.err.println("  java -DoldFullName=com.old.package.ClassName -DnewFullName=com.new.package.ClassName Main /path/to/project/src");
            System.err.println("\nDefault transformation fixes:");
            System.err.println("  com.github.javaparser.printer.PrettyPrinterConfiguration");
            System.err.println("  -> com.github.javaparser.printer.configuration.PrettyPrinterConfiguration");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        String oldFullName, newFullName;
        if (args.length == 3) {
            oldFullName = args[1];
            newFullName = args[2];
        } else {
            oldFullName = System.getProperty("oldFullName", "com.github.javaparser.printer.PrettyPrinterConfiguration");
            newFullName = System.getProperty("newFullName", "com.github.javaparser.printer.configuration.PrettyPrinterConfiguration");
        }
        
        System.out.println("Transforming Java files in: " + sourceDir);
        System.out.println("Fixing breaking change: " + oldFullName + " -> " + newFullName);
        
        try {
            Main transformer = new Main(oldFullName, newFullName);
            int totalFiles = transformer.findJavaFiles(sourceDir).size();
            int transformedCount = transformer.transformDirectory(sourceDir);
            
            System.out.println("\nTransformation complete!");
            System.out.println("Modified " + transformedCount + " out of " + totalFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}