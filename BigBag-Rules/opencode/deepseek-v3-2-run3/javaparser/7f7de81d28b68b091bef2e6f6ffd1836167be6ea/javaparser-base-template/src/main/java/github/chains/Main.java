package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for fixing snmp4j-agent 3.6.6 breaking API changes.
 * 
 * Breaking Change: Raw types in snmp4j-agent API have been updated to use generics.
 * 
 * Transformations applied:
 * 1. SortedMap<MOScope, ManagedObject> → SortedMap<MOScope, ManagedObject<?>>
 * 2. List<ManagedObject> → List<ManagedObject<?>>
 * 3. Raw ManagedObject type references → ManagedObject<?>
 * 4. Class implements ManagedObject → implements ManagedObject<SubRequest<?>>
 * 5. Method parameters: SubRequest → SubRequest<?> (in ManagedObject implementations)
 * 
 * This rule is generic and reusable for any project affected by the same
 * breaking change in snmp4j-agent 3.6.6.
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            int totalFiles = javaFiles.size();
            int processedFiles = 0;
            int modifiedFiles = 0;
            
            for (Path javaFile : javaFiles) {
                processedFiles++;
                System.out.printf("Processing file %d/%d: %s%n", processedFiles, totalFiles, javaFile);
                
                try {
                    JavaParser javaParser = new JavaParser();
                    ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
                    if (!parseResult.isSuccessful()) {
                        System.err.println("Failed to parse: " + javaFile);
                        continue;
                    }
                    CompilationUnit cu = parseResult.getResult().get();
                    
                    boolean modified = false;
                    
                    // Apply all transformations
                    modified |= fixSortedMapManagedObject(cu);
                    modified |= fixListManagedObject(cu);
                    modified |= fixRawManagedObjectTypes(cu);
                    modified |= fixSubRequestParameters(cu);
                    
                    if (modified) {
                        Files.write(javaFile, cu.toString().getBytes());
                        modifiedFiles++;
                        System.out.println("  -> Modified");
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
            
            System.out.printf("\nSummary: Processed %d files, modified %d files%n", totalFiles, modifiedFiles);
            System.out.println("\nTransformations applied:");
            System.out.println("1. Raw ManagedObject → ManagedObject<?> in type references");
            System.out.println("2. SortedMap<MOScope, ManagedObject> → SortedMap<MOScope, ManagedObject<?>>");
            System.out.println("3. List<ManagedObject> → List<ManagedObject<?>>");
            System.out.println("4. implements ManagedObject → implements ManagedObject<SubRequest<?>>");
            System.out.println("5. SubRequest parameters → SubRequest<?> in ManagedObject methods");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean fixSortedMapManagedObject(CompilationUnit cu) {
        boolean modified = false;
        
        List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);
        
        for (ClassOrInterfaceType type : types) {
            if (type.getNameAsString().equals("SortedMap") && 
                type.getTypeArguments().isPresent() &&
                type.getTypeArguments().get().size() == 2) {
                
                Type valueType = type.getTypeArguments().get().get(1);
                
                if (valueType.isClassOrInterfaceType() &&
                    valueType.asClassOrInterfaceType().getNameAsString().equals("ManagedObject") &&
                    !valueType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                    
                    ClassOrInterfaceType newValueType = new ClassOrInterfaceType();
                    newValueType.setName("ManagedObject");
                    WildcardType wildcard = new WildcardType();
                    newValueType.setTypeArguments(wildcard);
                    
                    type.getTypeArguments().get().set(1, newValueType);
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    private static boolean fixListManagedObject(CompilationUnit cu) {
        boolean modified = false;
        
        List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);
        
        for (ClassOrInterfaceType type : types) {
            String typeName = type.getNameAsString();
            if ((typeName.equals("List") || typeName.equals("ArrayList") || 
                 typeName.equals("Collection") || typeName.equals("Set")) &&
                type.getTypeArguments().isPresent() &&
                type.getTypeArguments().get().size() == 1) {
                
                Type elementType = type.getTypeArguments().get().get(0);
                
                if (elementType.isClassOrInterfaceType() &&
                    elementType.asClassOrInterfaceType().getNameAsString().equals("ManagedObject") &&
                    !elementType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                    
                    ClassOrInterfaceType newElementType = new ClassOrInterfaceType();
                    newElementType.setName("ManagedObject");
                    WildcardType wildcard = new WildcardType();
                    newElementType.setTypeArguments(wildcard);
                    
                    type.getTypeArguments().get().set(0, newElementType);
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    private static boolean fixRawManagedObjectTypes(CompilationUnit cu) {
        boolean modified = false;
        
        List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);
        
        for (ClassOrInterfaceType type : types) {
            if (type.getNameAsString().equals("ManagedObject") &&
                !type.getTypeArguments().isPresent()) {
                
                // Check if this is in an implements clause
                boolean isImplements = false;
                String parentStr = type.getParentNode().map(Object::toString).orElse("");
                if (parentStr.contains("implements") || parentStr.contains("extends")) {
                    isImplements = true;
                }
                
                if (isImplements) {
                    // For implements clauses: ManagedObject<SubRequest<?>>
                    ClassOrInterfaceType subRequestType = new ClassOrInterfaceType();
                    subRequestType.setName("SubRequest");
                    WildcardType wildcard = new WildcardType();
                    subRequestType.setTypeArguments(wildcard);
                    type.setTypeArguments(subRequestType);
                } else {
                    // For type references: ManagedObject<?>
                    WildcardType wildcard = new WildcardType();
                    type.setTypeArguments(wildcard);
                }
                modified = true;
            }
        }
        
        return modified;
    }
    
    private static boolean fixSubRequestParameters(CompilationUnit cu) {
        boolean modified = false;
        
        // Find method parameters with raw SubRequest type
        // This is a simplified approach - in a real implementation we'd need
        // to check if the method is from ManagedObject interface
        
        List<com.github.javaparser.ast.body.Parameter> params = 
            cu.findAll(com.github.javaparser.ast.body.Parameter.class);
        
        for (com.github.javaparser.ast.body.Parameter param : params) {
            Type paramType = param.getType();
            if (paramType.isClassOrInterfaceType()) {
                ClassOrInterfaceType type = paramType.asClassOrInterfaceType();
                if (type.getNameAsString().equals("SubRequest") &&
                    !type.getTypeArguments().isPresent()) {
                    
                    // Change SubRequest to SubRequest<?>
                    WildcardType wildcard = new WildcardType();
                    type.setTypeArguments(wildcard);
                    modified = true;
                }
            }
        }
        
        return modified;
    }
}