package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - these can be changed for different projects
    private static final String CONSTRUCTOR_CLASS = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String REPRESENTER_CLASS = "org.yaml.snakeyaml.representer.Representer";
    private static final String LOADER_OPTIONS_CLASS = "org.yaml.snakeyaml.LoaderOptions";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path javaFile) {
        try {
            String content = Files.readString(javaFile);
            JavaParser parser = new JavaParser();
            ParseResult<CompilationUnit> parseResult = parser.parse(content);
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse " + javaFile + ": " + parseResult.getProblems());
                return false;
            }
            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                System.err.println("No compilation unit found for " + javaFile);
                return false;
            }
            boolean modified = false;
            
            // Transform Constructor invocations
            modified |= transformConstructorInvocations(cu);
            
            // Transform Representer invocations  
            modified |= transformRepresenterInvocations(cu);
            
            // Transform getProperties method signatures
            modified |= transformGetPropertiesMethod(cu);
            
            if (modified) {
                Files.writeString(javaFile, cu.toString());
                System.out.println("Transformed: " + javaFile);
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error reading/writing file " + javaFile + ": " + e.getMessage());
        }
        return false;
    }
    
    private static boolean transformConstructorInvocations(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all ObjectCreationExpr nodes that create Constructor instances
        List<ObjectCreationExpr> constructorCreations = cu.findAll(ObjectCreationExpr.class, 
            expr -> {
                Type type = expr.getType();
                if (type instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
                    return classType.getNameAsString().equals("Constructor") && 
                           isSnakeYamlConstructor(classType);
                }
                return false;
            });
        
        for (ObjectCreationExpr expr : constructorCreations) {
            // Check if this is the old pattern: Constructor(Class<?>)
            if (expr.getArguments().size() == 1 && 
                expr.getArguments().get(0) instanceof ClassExpr) {
                
                // Transform to new pattern: Constructor(LoaderOptions)
                ObjectCreationExpr loaderOptions = new ObjectCreationExpr(
                    null, 
                    new ClassOrInterfaceType(null, LOADER_OPTIONS_CLASS),
                    NodeList.nodeList()
                );
                
                expr.getArguments().set(0, loaderOptions);
                modified = true;
                System.out.println("  - Transformed Constructor(Class) to Constructor(LoaderOptions)");
            }
        }
        
        return modified;
    }
    
    private static boolean transformRepresenterInvocations(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all ObjectCreationExpr nodes that create Representer instances
        List<ObjectCreationExpr> representerCreations = cu.findAll(ObjectCreationExpr.class, 
            expr -> {
                Type type = expr.getType();
                if (type instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
                    return classType.getNameAsString().equals("Representer") && 
                           isSnakeYamlRepresenter(classType);
                }
                return false;
            });
        
        for (ObjectCreationExpr expr : representerCreations) {
            // Check if this is the old pattern: Representer() - no arguments
            if (expr.getArguments().isEmpty()) {
                // Transform to new pattern: Representer(LoaderOptions)
                ObjectCreationExpr loaderOptions = new ObjectCreationExpr(
                    null, 
                    new ClassOrInterfaceType(null, LOADER_OPTIONS_CLASS),
                    NodeList.nodeList()
                );
                
                expr.getArguments().add(loaderOptions);
                modified = true;
                System.out.println("  - Transformed Representer() to Representer(LoaderOptions)");
            }
        }
        
        return modified;
    }
    
    private static boolean transformGetPropertiesMethod(CompilationUnit cu) {
        boolean modified = false;
        
        // Find all MethodDeclaration nodes that override getProperties
        List<MethodDeclaration> getPropertiesMethods = cu.findAll(MethodDeclaration.class, 
            method -> {
                if (!method.getNameAsString().equals("getProperties")) {
                    return false;
                }
                
                // Check if this method overrides Representer.getProperties
                if (method.getParameters().size() != 1) {
                    return false;
                }
                
                // Check if the method is in a class that extends Representer
                return method.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(cls -> cls.getExtendedTypes()
                        .stream()
                        .anyMatch(type -> type.getNameAsString().equals("Representer") && 
                                         isSnakeYamlRepresenter(type)))
                    .orElse(false);
            });
        
        for (MethodDeclaration method : getPropertiesMethods) {
            // Check if the method throws IntrospectionException
            boolean throwsIntrospectionException = method.getThrownExceptions()
                .stream()
                .anyMatch(exception -> {
                    if (exception instanceof ClassOrInterfaceType) {
                        return ((ClassOrInterfaceType) exception).getNameAsString().equals("IntrospectionException");
                    }
                    return false;
                });
            
            if (throwsIntrospectionException) {
                // Replace IntrospectionException with Exception
                method.getThrownExceptions().removeIf(
                    exception -> {
                        if (exception instanceof ClassOrInterfaceType) {
                            return ((ClassOrInterfaceType) exception).getNameAsString().equals("IntrospectionException");
                        }
                        return false;
                    }
                );
                method.addThrownException(new ClassOrInterfaceType(null, "Exception"));
                modified = true;
                System.out.println("  - Transformed getProperties throws clause from IntrospectionException to Exception");
            }
        }
        
        return modified;
    }
    
    private static boolean isSnakeYamlConstructor(ClassOrInterfaceType type) {
        // Check if this is org.yaml.snakeyaml.constructor.Constructor
        if (type.getScope().isPresent()) {
            String scopeName = type.getScope().get().asString();
            return scopeName.equals("org.yaml.snakeyaml.constructor") || 
                   scopeName.startsWith("org.yaml.snakeyaml");
        }
        
        // Check imports in the compilation unit
        CompilationUnit cu = type.findCompilationUnit().orElse(null);
        if (cu != null) {
            return cu.getImports().stream()
                .anyMatch(importDecl -> 
                    importDecl.getNameAsString().equals(CONSTRUCTOR_CLASS) ||
                    importDecl.getNameAsString().startsWith("org.yaml.snakeyaml"));
        }
        
        return false;
    }
    
    private static boolean isSnakeYamlRepresenter(ClassOrInterfaceType type) {
        // Check if this is org.yaml.snakeyaml.representer.Representer
        if (type.getScope().isPresent()) {
            String scopeName = type.getScope().get().asString();
            return scopeName.equals("org.yaml.snakeyaml.representer") || 
                   scopeName.startsWith("org.yaml.snakeyaml");
        }
        
        // Check imports in the compilation unit
        CompilationUnit cu = type.findCompilationUnit().orElse(null);
        if (cu != null) {
            return cu.getImports().stream()
                .anyMatch(importDecl -> 
                    importDecl.getNameAsString().equals(REPRESENTER_CLASS) ||
                    importDecl.getNameAsString().startsWith("org.yaml.snakeyaml"));
        }
        
        return false;
    }
}