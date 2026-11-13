package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.Name;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A generic transformation rule for fixing package relocations in Java code.
 * 
 * This rule handles the common breaking change where a class moves from one package to another.
 * It updates both import statements and fully-qualified class references in the code.
 * 
 * Example: org.mockito.runners.MockitoJUnitRunner -> org.mockito.junit.MockitoJUnitRunner
 */
public class Main {
    
    /**
     * Configuration for a package relocation fix.
     */
    static class PackageRelocationFix {
        final String oldFullyQualifiedName;
        final String newFullyQualifiedName;
        final String oldPackage;
        final String newPackage;
        final String className;
        
        PackageRelocationFix(String oldPackage, String newPackage, String className) {
            this.oldPackage = oldPackage;
            this.newPackage = newPackage;
            this.className = className;
            this.oldFullyQualifiedName = oldPackage + "." + className;
            this.newFullyQualifiedName = newPackage + "." + className;
        }
        
        PackageRelocationFix(String oldFullyQualifiedName, String newFullyQualifiedName) {
            this.oldFullyQualifiedName = oldFullyQualifiedName;
            this.newFullyQualifiedName = newFullyQualifiedName;
            int lastDotOld = oldFullyQualifiedName.lastIndexOf('.');
            int lastDotNew = newFullyQualifiedName.lastIndexOf('.');
            this.oldPackage = oldFullyQualifiedName.substring(0, lastDotOld);
            this.newPackage = newFullyQualifiedName.substring(0, lastDotNew);
            this.className = oldFullyQualifiedName.substring(lastDotOld + 1);
        }
        
        boolean matchesImport(ImportDeclaration importDecl) {
            String importedName = importDecl.getNameAsString();
            return importedName.equals(oldFullyQualifiedName) || 
                   (importDecl.isAsterisk() && importedName.equals(oldPackage));
        }
        
        boolean matchesTypeReference(String typeName) {
            return typeName.equals(oldFullyQualifiedName) ||
                   typeName.equals(oldFullyQualifiedName + "[]") ||
                   (typeName.startsWith(oldFullyQualifiedName + "<") && typeName.length() > oldFullyQualifiedName.length() + 1);
        }
        
        boolean matchesPackage(String packageName) {
            return packageName.equals(oldPackage) || packageName.startsWith(oldPackage + ".");
        }
    }
    
    /**
     * The main transformation method.
     * 
     * @param sourceDirectory Root directory containing Java source files
     * @param fixes List of package relocation fixes to apply
     * @throws IOException If there's an error reading or writing files
     */
    public static void transform(String sourceDirectory, List<PackageRelocationFix> fixes) throws IOException {
        Path sourcePath = Paths.get(sourceDirectory);
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int totalChanges = 0;
        int filesChanged = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                JavaParser parser = new JavaParser();
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
                if (!parseResult.isSuccessful()) {
                    System.err.println("Failed to parse " + javaFile + ": " + parseResult.getProblems());
                    continue;
                }
                CompilationUnit cu = parseResult.getResult().get();
                boolean fileChanged = false;
                
                // Track which fixes apply to this file based on imports
                Map<String, PackageRelocationFix> applicableFixes = new HashMap<>();
                
                // Fix import declarations
                for (ImportDeclaration importDecl : cu.findAll(ImportDeclaration.class)) {
                    for (PackageRelocationFix fix : fixes) {
                        if (fix.matchesImport(importDecl)) {
                            if (importDecl.isAsterisk()) {
                                // Handle wildcard imports
                                importDecl.setName(new Name(fix.newPackage));
                            } else {
                                // Handle specific class imports
                                importDecl.setName(new Name(fix.newFullyQualifiedName));
                            }
                            fileChanged = true;
                            totalChanges++;
                            System.out.println("Fixed import in " + javaFile + ": " + 
                                fix.oldFullyQualifiedName + " -> " + fix.newFullyQualifiedName);
                            
                            // Record that this fix applies to this file
                            applicableFixes.put(fix.className, fix);
                        }
                    }
                }
                
                // Fix fully-qualified class references in type declarations
                for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                    String typeName = type.getNameAsString();
                    
                    // Check for fully qualified names
                    for (PackageRelocationFix fix : fixes) {
                        if (fix.matchesTypeReference(typeName)) {
                            String newName = typeName.replace(fix.oldFullyQualifiedName, fix.newFullyQualifiedName);
                            type.setName(newName);
                            fileChanged = true;
                            totalChanges++;
                            System.out.println("Fixed type reference in " + javaFile + ": " + 
                                typeName + " -> " + newName);
                        }
                    }
                    
                    // Also check nested types with scope
                    if (type.getScope().isPresent()) {
                        String scopeName = type.getScope().get().toString();
                        for (PackageRelocationFix fix : fixes) {
                            if (fix.matchesTypeReference(scopeName + "." + typeName)) {
                                String newScope = scopeName.replace(fix.oldPackage, fix.newPackage);
                                type.getScope().get().replace(new Name(newScope));
                                fileChanged = true;
                                totalChanges++;
                                System.out.println("Fixed scoped type reference in " + javaFile + ": " + 
                                    scopeName + "." + typeName + " -> " + newScope + "." + typeName);
                            }
                        }
                    }
                }
                
                // Fix class expressions (e.g., MockitoJUnitRunner.class)
                for (ClassExpr classExpr : cu.findAll(ClassExpr.class)) {
                    if (classExpr.getType() instanceof ClassOrInterfaceType) {
                        ClassOrInterfaceType type = (ClassOrInterfaceType) classExpr.getType();
                        String typeName = type.getNameAsString();
                        
                        // Simple string matching for the class name
                        for (PackageRelocationFix fix : applicableFixes.values()) {
                            if (typeName.equals(fix.className)) {
                                // This might be using the class via import
                                System.out.println("Found class expression for " + fix.className + " in " + javaFile);
                            }
                        }
                    }
                }
                
                // Fix field accesses (e.g., SomeClass.SOME_FIELD)
                for (FieldAccessExpr fieldAccess : cu.findAll(FieldAccessExpr.class)) {
                    // Simple check for field accesses
                    if (fieldAccess.getScope() instanceof NameExpr) {
                        NameExpr nameExpr = (NameExpr) fieldAccess.getScope();
                        for (PackageRelocationFix fix : applicableFixes.values()) {
                            if (nameExpr.getNameAsString().equals(fix.className)) {
                                System.out.println("Found field access on " + fix.className + " in " + javaFile);
                            }
                        }
                    }
                }
                
                // Fix any remaining string occurrences in comments or string literals
                // (Less common but could be in comments or documentation)
                String fileContent = Files.readString(javaFile);
                for (PackageRelocationFix fix : fixes) {
                    if (fileContent.contains(fix.oldFullyQualifiedName)) {
                        System.out.println("Warning: Found string reference to " + fix.oldFullyQualifiedName + 
                            " in " + javaFile + " (may be in comments or string literals)");
                    }
                }
                
if (fileChanged) {
                    filesChanged++;
                    // Write the modified file back
                    String newContent = cu.toString();
                    Files.write(javaFile, newContent.getBytes());
                }
                
            } catch (FileNotFoundException e) {
                System.err.println("Could not read file: " + javaFile);
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\nTransformation complete!");
        System.out.println("Files changed: " + filesChanged);
        System.out.println("Total changes: " + totalChanges);
        System.out.println("\nNote: Some references may need manual review if they are:");
        System.out.println("1. In comments or documentation");
        System.out.println("2. In string literals");
        System.out.println("3. Dynamically loaded via reflection");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Example: java Main /path/to/project/src");
            System.err.println("\nOptional parameters:");
            System.err.println("  --fix <old-package> <new-package> <class-name>");
            System.err.println("  --fix <old-fqn> <new-fqn>");
            System.err.println("\nPredefined fixes for Mockito 4.x:");
            System.err.println("  MockitoJUnitRunner: org.mockito.runners -> org.mockito.junit");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        
        // Define the package relocation fixes
        List<PackageRelocationFix> fixes = new ArrayList<>();
        
        // Check for custom fixes in arguments
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--fix") && i + 2 < args.length) {
                if (i + 3 < args.length && !args[i + 3].startsWith("--")) {
                    // Format: --fix <old-package> <new-package> <class-name>
                    fixes.add(new PackageRelocationFix(args[i + 1], args[i + 2], args[i + 3]));
                    i += 3;
                } else {
                    // Format: --fix <old-fqn> <new-fqn>
                    fixes.add(new PackageRelocationFix(args[i + 1], args[i + 2]));
                    i += 2;
                }
            }
        }
        
        // If no custom fixes specified, use default Mockito 4.x fixes
        if (fixes.isEmpty()) {
            // MockitoJUnitRunner moved from runners to junit package
            fixes.add(new PackageRelocationFix(
                "org.mockito.runners",
                "org.mockito.junit", 
                "MockitoJUnitRunner"
            ));
            
            // Add other commonly relocated Mockito classes if needed
            // fixes.add(new PackageRelocationFix(
            //     "org.mockito.Matchers",
            //     "org.mockito.ArgumentMatchers",
            //     "any"
            // ));
        }
        
        try {
            System.out.println("Applying package relocation fixes to: " + sourceDirectory);
            System.out.println("Fixes to apply:");
            for (PackageRelocationFix fix : fixes) {
                System.out.println("  " + fix.oldFullyQualifiedName + " -> " + fix.newFullyQualifiedName);
            }
            System.out.println();
            
            transform(sourceDirectory, fixes);
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}