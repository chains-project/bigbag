package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for libthrift 0.16.0 breaking change:
 * TFastFramedTransport moved from org.apache.thrift.transport to 
 * org.apache.thrift.transport.layered
 */
public class Main {
    
    // Configuration parameters - can be changed for different projects
    private static final String OLD_PACKAGE = "org.apache.thrift.transport";
    private static final String NEW_PACKAGE = "org.apache.thrift.transport.layered";
    private static final String CLASS_NAME = "TFastFramedTransport";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile.toFile())) {
                    modifiedFiles++;
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static boolean processFile(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(input).getResult().orElse(null);
            
            if (cu == null) {
                return false;
            }
            
            LexicalPreservingPrinter.setup(cu);
            boolean modified = false;
            
            // Process import declarations
            List<ImportDeclaration> imports = cu.getImports();
            for (ImportDeclaration importDecl : imports) {
                if (importDecl.getNameAsString().equals(OLD_PACKAGE + "." + CLASS_NAME)) {
                    importDecl.setName(new Name(NEW_PACKAGE + "." + CLASS_NAME));
                    modified = true;
                    System.out.println("Updated import in " + file.getName() + ": " + 
                            OLD_PACKAGE + "." + CLASS_NAME + " -> " + NEW_PACKAGE + "." + CLASS_NAME);
                }
            }
            
            // Process fully qualified class names in code
            List<ClassOrInterfaceType> classTypes = cu.findAll(ClassOrInterfaceType.class);
            for (ClassOrInterfaceType classType : classTypes) {
                if (classType.getNameAsString().equals(CLASS_NAME)) {
                    // Check if this is a fully qualified name reference
                    if (classType.getScope().isPresent()) {
                        ClassOrInterfaceType scope = classType.getScope().get();
                        if (scope.getNameAsString().equals(OLD_PACKAGE)) {
                            classType.setScope(new ClassOrInterfaceType(null, NEW_PACKAGE));
                            modified = true;
                            System.out.println("Updated fully qualified reference in " + file.getName() + ": " + 
                                    OLD_PACKAGE + "." + CLASS_NAME + " -> " + NEW_PACKAGE + "." + CLASS_NAME);
                        }
                    }
                }
            }
            
            if (modified) {
                try (FileOutputStream output = new FileOutputStream(file)) {
                    output.write(LexicalPreservingPrinter.print(cu).getBytes());
                }
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Error processing file " + file.getName() + ": " + e.getMessage());
        }
        
        return false;
    }
}