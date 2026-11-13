package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Main {
    // Configuration for breaking changes
    private static final Map<String, String> IMPORT_TRANSFORMATIONS = new HashMap<>();
    private static final Map<String, String> TYPE_TRANSFORMATIONS = new HashMap<>();
    private static final List<String> REMOVED_IMPORTS = new ArrayList<>();
    
    static {
        // Pattern 1: Scalar class name simplifications
        IMPORT_TRANSFORMATIONS.put("org.cactoos.scalar.IoCheckedScalar", "org.cactoos.scalar.IoChecked");
        IMPORT_TRANSFORMATIONS.put("org.cactoos.scalar.SolidScalar", "org.cactoos.scalar.Solid");
        IMPORT_TRANSFORMATIONS.put("org.cactoos.scalar.StickyScalar", "org.cactoos.scalar.Sticky");
        IMPORT_TRANSFORMATIONS.put("org.cactoos.scalar.UncheckedScalar", "org.cactoos.scalar.Unchecked");
        IMPORT_TRANSFORMATIONS.put("org.cactoos.scalar.CheckedScalar", "org.cactoos.scalar.Checked");
        
        TYPE_TRANSFORMATIONS.put("IoCheckedScalar", "IoChecked");
        TYPE_TRANSFORMATIONS.put("SolidScalar", "Solid");
        TYPE_TRANSFORMATIONS.put("StickyScalar", "Sticky");
        TYPE_TRANSFORMATIONS.put("UncheckedScalar", "Unchecked");
        TYPE_TRANSFORMATIONS.put("CheckedScalar", "Checked");
        
        // Pattern 2: Text class name simplifications
        IMPORT_TRANSFORMATIONS.put("org.cactoos.text.JoinedText", "org.cactoos.text.Joined");
        IMPORT_TRANSFORMATIONS.put("org.cactoos.text.SplitText", "org.cactoos.text.Split");
        IMPORT_TRANSFORMATIONS.put("org.cactoos.text.TrimmedText", "org.cactoos.text.Trimmed");
        IMPORT_TRANSFORMATIONS.put("org.cactoos.text.RandomText", "org.cactoos.text.Randomized");
        
        TYPE_TRANSFORMATIONS.put("JoinedText", "Joined");
        TYPE_TRANSFORMATIONS.put("SplitText", "Split");
        TYPE_TRANSFORMATIONS.put("TrimmedText", "Trimmed");
        TYPE_TRANSFORMATIONS.put("RandomText", "Randomized");
        
        // Pattern 3: Package relocations
        IMPORT_TRANSFORMATIONS.put("org.cactoos.iterable.LengthOf", "org.cactoos.scalar.LengthOf");
        TYPE_TRANSFORMATIONS.put("LengthOf", "LengthOf"); // Name stays same, just package changes
        
        // Pattern 4: Removed classes (need manual fixing)
        REMOVED_IMPORTS.add("org.cactoos.collection.CollectionOf");
        
        // Pattern 5: Package changes
        IMPORT_TRANSFORMATIONS.put("org.cactoos.collection.Filtered", "org.cactoos.iterable.Filtered");
        TYPE_TRANSFORMATIONS.put("Filtered", "Filtered"); // Name stays same, just package changes
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("This tool applies generic transformations for cactoos 0.55.0 breaking changes");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying generic transformations to: " + sourceDir);
        System.out.println("Transformations configured:");
        System.out.println("  - " + IMPORT_TRANSFORMATIONS.size() + " import transformations");
        System.out.println("  - " + TYPE_TRANSFORMATIONS.size() + " type transformations");
        System.out.println("  - " + REMOVED_IMPORTS.size() + " removed imports (require manual fix)");
        System.out.println("\nImport transformations:");
        for (Map.Entry<String, String> entry : IMPORT_TRANSFORMATIONS.entrySet()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }
        
        try {
            transformDirectory(new File(sourceDir));
            System.out.println("Transformation completed.");
            
            if (!REMOVED_IMPORTS.isEmpty()) {
                System.out.println("\nWARNING: The following imports reference removed classes and require manual fixes:");
                for (String removedImport : REMOVED_IMPORTS) {
                    System.out.println("  - " + removedImport);
                }
            }
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(File directory) throws Exception {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory.getPath());
        }
        
        List<File> javaFiles = findJavaFiles(directory);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        for (File javaFile : javaFiles) {
            Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
            if (!cuOpt.isPresent()) {
                System.err.println("Failed to parse: " + javaFile.getPath());
                continue;
            }
            
            CompilationUnit cu = cuOpt.get();
            TransformationResult result = transformCompilationUnit(cu);
            
            if (result.modified) {
                transformedFiles++;
                totalTransformations += result.transformationsApplied;
                
                // Write back the modified file
                Files.write(javaFile.toPath(), cu.toString().getBytes());
                System.out.println("Transformed " + javaFile.getPath() + " (" + result.transformationsApplied + " changes)");
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  - Transformed " + transformedFiles + " files");
        System.out.println("  - Applied " + totalTransformations + " transformations total");
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }
    
    private static TransformationResult transformCompilationUnit(CompilationUnit cu) {
        TransformationResult result = new TransformationResult();
        
        // Transform imports
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            // Check for removed imports
            if (REMOVED_IMPORTS.contains(importName)) {
                importsToRemove.add(importDecl);
                result.transformationsApplied++;
                result.modified = true;
                continue;
            }
            
            // Apply import transformations
            if (IMPORT_TRANSFORMATIONS.containsKey(importName)) {
                String newImportName = IMPORT_TRANSFORMATIONS.get(importName);
                importDecl.setName(newImportName);
                result.transformationsApplied++;
                result.modified = true;
            }
        }
        
        // Remove imports for deleted classes
        cu.getImports().removeAll(importsToRemove);
        
        // Transform type references in the AST
        cu.accept(new com.github.javaparser.ast.visitor.ModifierVisitor<Void>() {
            @Override
            public Node visit(ClassOrInterfaceType n, Void arg) {
                ClassOrInterfaceType resultType = (ClassOrInterfaceType) super.visit(n, arg);
                
                // Apply type name transformations
                String typeName = resultType.getNameAsString();
                if (TYPE_TRANSFORMATIONS.containsKey(typeName)) {
                    String newTypeName = TYPE_TRANSFORMATIONS.get(typeName);
                    resultType.setName(newTypeName);
                    result.transformationsApplied++;
                    result.modified = true;
                }
                
                return resultType;
            }
        }, null);
        
        return result;
    }
    
    private static class TransformationResult {
        boolean modified = false;
        int transformationsApplied = 0;
    }
}