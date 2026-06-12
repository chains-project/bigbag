package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int totalModified = 0;
            JavaParser parser = new JavaParser();
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) continue;
                    
                    boolean modified = false;
                    
                    // Find all classes that extend Representer or its subclasses
                    List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                    
                    for (ClassOrInterfaceDeclaration clazz : classes) {
                        // Check if this class extends Representer or SafeRepresenter
                        if (extendsRepresenter(clazz)) {
                            // Find all methods in this class
                            List<MethodDeclaration> methods = clazz.getMethods();
                            for (MethodDeclaration method : methods) {
                                // Check if method is getProperties with right signature
                                if (isGetPropertiesMethod(method)) {
                                    // Check if method throws IntrospectionException
                                    if (hasIntrospectionException(method)) {
                                        // Remove IntrospectionException from throws clause
                                        removeIntrospectionException(method);
                                        modified = true;
                                        System.out.println("Fixed getProperties method in: " + javaFile + " - " + clazz.getNameAsString());
                                    }
                                }
                                // Also check for methods that call super.getProperties() and declare IntrospectionException
                                else if (method.getThrownExceptions().stream().anyMatch(e -> e.asString().equals("IntrospectionException"))) {
                                    // Check if method calls super.getProperties()
                                    if (callsSuperGetProperties(method)) {
                                        // Remove IntrospectionException from throws clause
                                        removeIntrospectionException(method);
                                        modified = true;
                                        System.out.println("Fixed method calling super.getProperties in: " + javaFile + " - " + clazz.getNameAsString() + "." + method.getNameAsString());
                                    }
                                }
                            }
                        }
                    }
                    
                    if (modified) {
                        // Write the modified file back
                        Files.write(javaFile, cu.toString().getBytes());
                        totalModified++;
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
            
            System.out.println("Total files modified: " + totalModified);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean extendsRepresenter(ClassOrInterfaceDeclaration clazz) {
        if (!clazz.isClassOrInterfaceDeclaration()) {
            return false;
        }
        
        // Check if class extends Representer or SafeRepresenter
        if (clazz.getExtendedTypes().isNonEmpty()) {
            for (ClassOrInterfaceType extendedType : clazz.getExtendedTypes()) {
                String typeName = extendedType.getNameAsString();
                if (typeName.equals("Representer") || typeName.equals("SafeRepresenter")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static boolean isGetPropertiesMethod(MethodDeclaration method) {
        // Check method name
        if (!method.getNameAsString().equals("getProperties")) {
            return false;
        }
        
        // Check if it has exactly one parameter
        if (method.getParameters().size() != 1) {
            return false;
        }
        
        // Check if parameter type is Class<? extends Object>
        com.github.javaparser.ast.type.Type paramType = method.getParameter(0).getType();
        String paramTypeStr = paramType.asString();
        
        // Accept various forms: Class, Class<?>, Class<? extends Object>
        return paramTypeStr.startsWith("Class") && 
               paramTypeStr.contains("Object");
    }
    
    private static boolean hasIntrospectionException(MethodDeclaration method) {
        if (method.getThrownExceptions().isEmpty()) {
            return false;
        }
        
        for (com.github.javaparser.ast.type.ReferenceType exceptionType : method.getThrownExceptions()) {
            if (exceptionType.asString().equals("IntrospectionException")) {
                return true;
            }
        }
        
        return false;
    }
    
    private static void removeIntrospectionException(MethodDeclaration method) {
        NodeList<com.github.javaparser.ast.type.ReferenceType> thrownExceptions = method.getThrownExceptions();
        List<com.github.javaparser.ast.type.ReferenceType> newExceptions = new ArrayList<>();
        
        for (com.github.javaparser.ast.type.ReferenceType exceptionType : thrownExceptions) {
            if (!exceptionType.asString().equals("IntrospectionException")) {
                newExceptions.add(exceptionType);
            }
        }
        
        method.setThrownExceptions(NodeList.nodeList(newExceptions));
    }
    
    private static boolean callsSuperGetProperties(MethodDeclaration method) {
        // Check if method body contains super.getProperties call
        if (!method.getBody().isPresent()) {
            return false;
        }
        
        String methodBody = method.getBody().get().toString();
        return methodBody.contains("super.getProperties") || methodBody.contains("super.getProperties(");
    }
}