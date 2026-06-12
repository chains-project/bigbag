package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    private static final Map<String, String> NAMESPACE_MAPPINGS = Map.of(
        "javax.mvc", "jakarta.mvc",
        "javax.ws.rs", "jakarta.ws.rs",
        "javax.servlet", "jakarta.servlet",
        "javax.annotation", "jakarta.annotation",
        "javax.enterprise", "jakarta.enterprise",
        "javax.inject", "jakarta.inject",
        "javax.validation", "jakarta.validation",
        "javax.persistence", "jakarta.persistence",
        "javax.ejb", "jakarta.ejb",
        "javax.transaction", "jakarta.transaction"
    );
    
    private static final JavaParser javaParser = new JavaParser();
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <sourceDirectory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        System.out.println("Applying namespace migrations:");
        NAMESPACE_MAPPINGS.forEach((oldNs, newNs) -> 
            System.out.println("  " + oldNs + ".* -> " + newNs + ".*"));
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
                System.err.println("Error: Source directory does not exist: " + sourceDir);
                System.exit(1);
            }
            
            List<Path> javaFiles = Files.walk(sourcePath)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int totalChanges = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFile);
                    if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                        System.err.println("Failed to parse: " + javaFile);
                        continue;
                    }
                    
                    CompilationUnit cu = parseResult.getResult().get();
                    boolean fileChanged = false;
                    int fileChanges = 0;
                    
                    fileChanged = transformImports(cu);
                    fileChanges += transformTypeReferences(cu);
                    
                    if (fileChanged || fileChanges > 0) {
                        Files.write(javaFile, cu.toString().getBytes());
                        transformedFiles++;
                        totalChanges += fileChanges;
                        System.out.println("Transformed: " + sourcePath.relativize(javaFile) + 
                                         " (" + fileChanges + " changes)");
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
            
            System.out.println("\nTransformation complete!");
            System.out.println("Files transformed: " + transformedFiles + "/" + javaFiles.size());
            System.out.println("Total changes made: " + totalChanges);
            
        } catch (IOException e) {
            System.err.println("Error walking directory: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static boolean transformImports(CompilationUnit cu) {
        boolean changed = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (ImportDeclaration importDecl : imports) {
            String importName = importDecl.getNameAsString();
            for (Map.Entry<String, String> mapping : NAMESPACE_MAPPINGS.entrySet()) {
                if (importName.startsWith(mapping.getKey())) {
                    String newImportName = importName.replace(mapping.getKey(), mapping.getValue());
                    importDecl.setName(new Name(newImportName));
                    changed = true;
                    break;
                }
            }
        }
        
        return changed;
    }
    
    private static int transformTypeReferences(CompilationUnit cu) {
        final int[] changeCount = {0};
        
        cu.walk(ClassOrInterfaceType.class, type -> {
            String typeName = type.getNameAsString();
            String typeNameWithScope = getFullTypeName(type);
            
            for (Map.Entry<String, String> mapping : NAMESPACE_MAPPINGS.entrySet()) {
                String oldPrefix = mapping.getKey();
                String newPrefix = mapping.getValue();
                
                if (typeNameWithScope.startsWith(oldPrefix)) {
                    String newTypeName = typeNameWithScope.replace(oldPrefix, newPrefix);
                    updateTypeName(type, newTypeName);
                    changeCount[0]++;
                    break;
                } else if (typeNameWithScope.contains("." + oldPrefix + ".")) {
                    String newTypeName = typeNameWithScope.replace("." + oldPrefix + ".", "." + newPrefix + ".");
                    updateTypeName(type, newTypeName);
                    changeCount[0]++;
                    break;
                }
            }
        });
        
        cu.walk(Type.class, type -> {
            if (type.isClassOrInterfaceType()) {
                return;
            }
            
            String typeString = type.toString();
            for (Map.Entry<String, String> mapping : NAMESPACE_MAPPINGS.entrySet()) {
                if (typeString.contains(mapping.getKey())) {
                    String newTypeString = typeString.replace(mapping.getKey(), mapping.getValue());
                    try {
                        ParseResult<Type> parseResult = javaParser.parseType(newTypeString);
                        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                            Type newType = parseResult.getResult().get();
                            type.replace(newType);
                            changeCount[0]++;
                        }
                    } catch (Exception e) {
                    }
                    break;
                }
            }
        });
        
        return changeCount[0];
    }
    
    private static String getFullTypeName(ClassOrInterfaceType type) {
        StringBuilder fullName = new StringBuilder();
        
        if (type.getScope().isPresent()) {
            fullName.append(getFullTypeName(type.getScope().get()));
            fullName.append(".");
        }
        
        fullName.append(type.getNameAsString());
        return fullName.toString();
    }
    
    private static void updateTypeName(ClassOrInterfaceType type, String newFullName) {
        String[] parts = newFullName.split("\\.");
        
        if (parts.length == 1) {
            type.setName(new SimpleName(parts[0]));
            type.setScope(null);
        } else {
            String lastName = parts[parts.length - 1];
            String scopeName = String.join(".", Arrays.copyOf(parts, parts.length - 1));
            
            type.setName(new SimpleName(lastName));
            
            ParseResult<ClassOrInterfaceType> parseResult = javaParser.parseClassOrInterfaceType(scopeName);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                type.setScope(parseResult.getResult().get());
            }
        }
    }
}