package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming hamcrest TypeSafeMatcher constructors in: " + sourceDir);
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(String sourceDir) throws Exception {
        List<Path> javaFiles = findAllJavaFiles(Paths.get(sourceDir));
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            CompilationUnit cu;
            try {
                cu = parser.parse(javaFile).getResult().orElse(null);
            } catch (FileNotFoundException e) {
                System.err.println("File not found: " + javaFile);
                continue;
            }
            
            if (cu == null) {
                System.err.println("Failed to parse: " + javaFile);
                continue;
            }
            
            boolean modified = false;
            
            // Find all classes that extend TypeSafeMatcher or TypeSafeDiagnosingMatcher
            List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
            
            for (ClassOrInterfaceDeclaration clazz : classes) {
                for (ClassOrInterfaceType extendedType : clazz.getExtendedTypes()) {
                    String typeName = extendedType.getNameAsString();
                    if ("TypeSafeMatcher".equals(typeName) || "TypeSafeDiagnosingMatcher".equals(typeName)) {
                        if (extendedType.getTypeArguments().isPresent() && 
                            !extendedType.getTypeArguments().get().isEmpty()) {
                            
                            String typeArgName = extendedType.getTypeArguments().get().get(0).toString();
                            
                            for (ConstructorDeclaration constructor : clazz.getConstructors()) {
                                List<SuperExpr> superCalls = constructor.getBody().findAll(SuperExpr.class);
                                for (SuperExpr superCall : superCalls) {
                                    // Check if this is super() with no arguments
                                    if (superCall.getArguments() == null || superCall.getArguments().isEmpty()) {
                                        // Create new arguments list with T.class
                                        NodeList args = new NodeList<>();
                                        args.add(new ClassExpr(new ClassOrInterfaceType(typeArgName)));
                                        superCall.setArguments(args);
                                        modified = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (modified) {
                System.out.println("Transformed: " + javaFile);
                cu.getStorage().ifPresent(storage -> {
                    try {
                        storage.save();
                    } catch (Exception e) {
                        System.err.println("Failed to save: " + javaFile);
                    }
                });
            }
        }
    }
    
    private static List<Path> findAllJavaFiles(Path startDir) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        File dir = startDir.toFile();
        
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Not a directory: " + startDir);
        }
        
        findAllJavaFilesRecursive(dir, javaFiles);
        return javaFiles;
    }
    
    private static void findAllJavaFilesRecursive(File dir, List<Path> javaFiles) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                findAllJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file.toPath());
            }
        }
    }
}