package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration: these should be parameters for the transformation
    // This is a generic package rename transformation
    // It transforms imports from old.package.* to new.package.*
    // and specific imports from old.package.ClassName to new.package.ClassName
    
    // For this specific Struts2 breaking change:
    private static final String OLD_PACKAGE_PREFIX = "org.apache.struts2.dispatcher.ng.filter";
    private static final String NEW_PACKAGE_PREFIX = "org.apache.struts2.dispatcher.filter";
    
    // For a fully generic solution, these would be command line arguments:
    // private static String oldPackagePrefix;
    // private static String newPackagePrefix;
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Transformation: " + OLD_PACKAGE_PREFIX + ".* -> " + NEW_PACKAGE_PREFIX + ".*");
        System.out.println("(This will rename all imports from the old package to the new package)");
        
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int filesModified = 0;
        int importsUpdated = 0;
        int typeReferencesUpdated = 0;
        
        for (Path javaFile : javaFiles) {
            boolean fileModified = false;
            
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                
                if (cu == null) {
                    continue;
                }
                
                // Check imports
                NodeList<ImportDeclaration> imports = cu.getImports();
                List<ImportDeclaration> importsToRemove = new ArrayList<>();
                List<ImportDeclaration> importsToAdd = new ArrayList<>();
                
                for (ImportDeclaration importDecl : imports) {
                    String importName = importDecl.getNameAsString();
                    
                    // Case 1: Wildcard import of the old package
                    if (importName.equals(OLD_PACKAGE_PREFIX + ".*")) {
                        importsToRemove.add(importDecl);
                        ImportDeclaration newImport = new ImportDeclaration(
                            new Name(NEW_PACKAGE_PREFIX + ".*"),
                            importDecl.isStatic(),
                            importDecl.isAsterisk()
                        );
                        importsToAdd.add(newImport);
                        importsUpdated++;
                        fileModified = true;
                    }
                    // Case 2: Specific class import from the old package
                    else if (importName.startsWith(OLD_PACKAGE_PREFIX + ".") && !importDecl.isAsterisk()) {
                        importsToRemove.add(importDecl);
                        // Extract the class name and any nested classes/members
                        String suffix = importName.substring(OLD_PACKAGE_PREFIX.length());
                        String newImportName = NEW_PACKAGE_PREFIX + suffix;
                        ImportDeclaration newImport = new ImportDeclaration(
                            new Name(newImportName),
                            importDecl.isStatic(),
                            importDecl.isAsterisk()
                        );
                        importsToAdd.add(newImport);
                        importsUpdated++;
                        fileModified = true;
                    }
                }
                
                // Apply import changes
                imports.removeAll(importsToRemove);
                imports.addAll(importsToAdd);
                
                // Note: Fully-qualified type references in code (not in imports) would require
                // more complex AST traversal. For most breaking API changes, updating imports
                // is sufficient since code typically uses imported types rather than
                // fully-qualified names. This transformation focuses on imports which
                // addresses the most common case.
                
                if (fileModified) {
                    // Write back the modified file
                    Files.write(javaFile, cu.toString().getBytes());
                    filesModified++;
                    System.out.println("Updated: " + javaFile);
                }
                
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files modified: " + filesModified);
        System.out.println("  Imports updated: " + importsUpdated);
        System.out.println("  Type references updated: " + typeReferencesUpdated);
    }
    
    private static List<Path> findJavaFiles(Path dir) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    // Note: For a more complete solution, we would add:
    // 1. Command-line argument parsing for oldPackage and newPackage
    // 2. AST visitor to handle fully-qualified type references in code
    // 3. Support for multiple package renames in a single run
    // 4. Better error handling and logging
    
    // The current implementation handles the common case: import statements.
    // Most Java code uses imports rather than fully-qualified names in the code body.
    // This transformation can be extended as needed for specific use cases.
}