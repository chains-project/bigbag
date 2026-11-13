package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Struts2 package relocation from 
 * org.apache.struts2.dispatcher.ng.filter to org.apache.struts2.dispatcher.filter
 * 
 * This transformation handles:
 * - Import statement updates
 * - Fully-qualified class name updates in code
 * - Type reference updates
 */
public class Main {
    
    // Configuration: Old and new package patterns
    private static final String OLD_PACKAGE_PREFIX = "org.apache.struts2.dispatcher.ng.filter";
    private static final String NEW_PACKAGE_PREFIX = "org.apache.struts2.dispatcher.filter";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Example: java -jar transformation.jar /path/to/project/src");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        System.out.println("Applying Struts2 package relocation transformation to: " + sourceDirectory);
        
        try {
            transformProject(Paths.get(sourceDirectory));
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
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }
    
    private static void transformFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
        
        // Apply transformation
        PackageRelocationVisitor visitor = new PackageRelocationVisitor();
        cu.accept(visitor, null);
        
        // Write transformed content back
        Files.writeString(javaFile, cu.toString());
    }
    
    /**
     * Visitor that transforms package references from old to new location
     */
    private static class PackageRelocationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import matches the old package pattern
            if (importName.startsWith(OLD_PACKAGE_PREFIX)) {
                String newImportName = importName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                System.out.println("Transforming import: " + importName + " -> " + newImportName);
                
                // Create new import declaration
                ImportDeclaration newImport = new ImportDeclaration(
                    new Name(newImportName),
                    importDecl.isStatic(),
                    importDecl.isAsterisk()
                );
                newImport.setComment(importDecl.getComment().orElse(null));
                return newImport;
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Check if this type name contains the old package pattern
            // This handles fully-qualified class names in code
            if (typeName.contains(OLD_PACKAGE_PREFIX)) {
                String newTypeName = typeName.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                System.out.println("Transforming type reference: " + typeName + " -> " + newTypeName);
                
                // Create new type with transformed name
                ClassOrInterfaceType newType = new ClassOrInterfaceType(null, newTypeName);
                newType.setTypeArguments(type.getTypeArguments().orElse(null));
                newType.setComment(type.getComment().orElse(null));
                return newType;
            }
            
            return (Node) super.visit(type, arg);
        }
        
        @Override
        public Node visit(Name name, Void arg) {
            String nameString = name.asString();
            
            // Check if this name contains the old package pattern
            // This handles fully-qualified class names in various contexts
            if (nameString.contains(OLD_PACKAGE_PREFIX)) {
                String newNameString = nameString.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                System.out.println("Transforming name reference: " + nameString + " -> " + newNameString);
                
                // Create new name with transformed package
                return new Name(newNameString);
            }
            
            return (Node) super.visit(name, arg);
        }
    }
}