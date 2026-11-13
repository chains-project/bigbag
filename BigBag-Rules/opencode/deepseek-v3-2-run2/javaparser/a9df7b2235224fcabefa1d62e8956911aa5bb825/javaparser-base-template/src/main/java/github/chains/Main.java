package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Generic JavaParser transformation to fix breaking API changes in dependencies.
 * 
 * This transformation removes specific exceptions from method throws clauses
 * in classes that extend a specified parent class.
 * 
 * For the snakeyaml 1.32 breaking change:
 * - Parent class: org.yaml.snakeyaml.representer.Representer
 * - Method name: getProperties  
 * - Exception to remove: IntrospectionException
 * 
 * Usage: java -jar transformation.jar <source-directory> <parent-class> <method-name> <exception-to-remove>
 *   If method-name is "*", removes exception from ALL methods in the class
 * Example: java -jar transformation.jar ./src org.yaml.snakeyaml.representer.Representer getProperties IntrospectionException
 * Example: java -jar transformation.jar ./src org.yaml.snakeyaml.representer.Representer * IntrospectionException
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: java -jar transformation.jar <source-directory> <parent-class> <method-name> <exception-to-remove>");
            System.err.println("Example: java -jar transformation.jar ./src org.yaml.snakeyaml.representer.Representer getProperties IntrospectionException");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String parentClassName = args[1];
        String methodName = args[2];
        String exceptionToRemove = args[3];
        
        System.out.println("Transformation parameters:");
        System.out.println("  Source directory: " + sourceDir);
        System.out.println("  Parent class: " + parentClassName);
        System.out.println("  Method name: " + methodName);
        System.out.println("  Exception to remove: " + exceptionToRemove);
        System.out.println();
        
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(javaFiles::add);
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int filesModified = 0;
        int[] methodsFixed = new int[]{0};
        
        for (Path javaFile : javaFiles) {
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                JavaParser parser = new JavaParser();
                Optional<CompilationUnit> cuOpt = parser.parse(in).getResult();
                
                if (cuOpt.isPresent()) {
                    CompilationUnit cu = cuOpt.get();
                    boolean[] modified = new boolean[]{false};
                    
                    // Get simple name of parent class (without package)
                    String parentSimpleName = parentClassName.contains(".") 
                        ? parentClassName.substring(parentClassName.lastIndexOf('.') + 1)
                        : parentClassName;
                    
                    // Check if the file imports the parent class
                    boolean importsParentClass = cu.getImports().stream()
                        .anyMatch(imp -> {
                            String importName = imp.getNameAsString();
                            return importName.equals(parentClassName) || 
                                   importName.endsWith("." + parentSimpleName);
                        });
                    
                    // Visitor to fix methods in subclasses of the specified parent
                    cu.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                            super.visit(n, arg);
                            
                            // Check if this class extends the specified parent class
                            boolean extendsParentClass = false;
                            
                            if (n.getExtendedTypes().isNonEmpty()) {
                                for (ClassOrInterfaceType ext : n.getExtendedTypes()) {
                                    String extName = ext.getNameAsString();
                                    
                                    // Check if extension matches simple name or fully qualified name
                                    if (extName.equals(parentSimpleName) || extName.equals(parentClassName)) {
                                        // For simple names, we need to check imports
                                        if (extName.equals(parentSimpleName) && !extName.equals(parentClassName)) {
                                            // Simple name - check if imported
                                            if (importsParentClass || isInSamePackage(cu, parentClassName)) {
                                                extendsParentClass = true;
                                            }
                                        } else {
                                            // Fully qualified name or simple name that matches exactly
                                            extendsParentClass = true;
                                        }
                                    }
                                    
                                    // Also check if it extends a class that extends our parent
                                    // (indirect inheritance - we would need to resolve types)
                                    // For simplicity, we only check direct inheritance
                                }
                            }
                            
                            if (extendsParentClass) {
                                // Visit all methods in this class
                                n.accept(new VoidVisitorAdapter<Void>() {
                                    @Override
                                    public void visit(MethodDeclaration m, Void arg) {
                                        super.visit(m, arg);
                                        
// Check if this is the target method
                                        if (methodName.equals("*") || m.getNameAsString().equals(methodName)) {
                                            final String exceptionName = exceptionToRemove;
                                            boolean hasException = m.getThrownExceptions().stream()
                                                .anyMatch(ex -> {
                                                    // Get the type name from ReferenceType
                                                    String typeName = ex.getElementType().asString();
                                                    return typeName.equals(exceptionName);
                                                });
                                            
                                            if (hasException) {
                                                // Remove the exception from throws clause
                                                m.getThrownExceptions().removeIf(
                                                    ex -> {
                                                        String typeName = ex.getElementType().asString();
                                                        return typeName.equals(exceptionName);
                                                    }
                                                );
                                                modified[0] = true;
                                                methodsFixed[0]++;
                                                System.out.println("Fixed " + m.getNameAsString() + " method in " + javaFile);
                                            }
                                        }
                                    }
                                }, null);
                            }
                        }
                    }, null);
                    
                    if (modified[0]) {
                        // Write the modified file back
                        Files.write(javaFile, cu.toString().getBytes());
                        filesModified++;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("Files processed: " + javaFiles.size());
        System.out.println("Files modified: " + filesModified);
        System.out.println("Methods fixed: " + methodsFixed[0]);
    }
    
    private static boolean isInSamePackage(CompilationUnit cu, String className) {
        // Very simplistic check - assumes default package
        // In a real implementation, we would need to resolve the full class name
        return false;
    }
}