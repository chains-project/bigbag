package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Hibernate UserType implementations in: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
            int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                transformedFiles++;
            }
        }
        
        System.out.println("Transformation complete. Modified " + transformedFiles + " files.");
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        boolean[] modified = {false};
        
        // First, check if this file implements UserType by looking for the interface
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration n, Void arg) {
                super.visit(n, arg);
                
                // Check if this class implements UserType
                boolean implementsUserType = n.getImplementedTypes().stream()
                    .anyMatch(type -> type.getNameAsString().equals("UserType"));
                
                if (implementsUserType) {
                    System.out.println("Found UserType implementation: " + n.getNameAsString() + " in " + javaFile);
                    
                    // Transform nullSafeGet and nullSafeSet methods
                    List<MethodDeclaration> methods = n.getMethods();
                    for (MethodDeclaration method : methods) {
                        String methodName = method.getNameAsString();
                        
                        if ("nullSafeGet".equals(methodName) || "nullSafeSet".equals(methodName)) {
                            // Check if method has @Override annotation, add if missing
                            boolean hasOverride = method.getAnnotations().stream()
                                .anyMatch(anno -> anno.getNameAsString().equals("Override"));
                            
                            if (!hasOverride) {
                                method.addAnnotation(new MarkerAnnotationExpr(new Name("Override")));
                                modified[0] = true;
                            }
                            
                            // Transform parameter types from SessionImplementor to SharedSessionContractImplementor
                            NodeList<Parameter> parameters = method.getParameters();
                            for (Parameter param : parameters) {
                                String paramType = param.getType().asString();
                                
                                if (paramType.equals("SessionImplementor")) {
                                    param.setType(new ClassOrInterfaceType(null, "SharedSessionContractImplementor"));
                                    modified[0] = true;
                                    System.out.println("  - Updated parameter type in " + methodName + " to SharedSessionContractImplementor");
                                }
                            }
                        }
                    }
                }
            }
            
            @Override
            public void visit(ImportDeclaration n, Void arg) {
                super.visit(n, arg);
                
                // Update imports from SessionImplementor to SharedSessionContractImplementor
                String importName = n.getNameAsString();
                if (importName.equals("org.hibernate.engine.spi.SessionImplementor")) {
                    n.setName(new Name("org.hibernate.engine.spi.SharedSessionContractImplementor"));
                    modified[0] = true;
                    System.out.println("  - Updated import to SharedSessionContractImplementor");
                }
            }
        }, null);
        
if (modified[0]) {
                // Write the transformed file back
                cu.getClassByName(cu.getPrimaryTypeName().orElse("")).ifPresent(cls -> {
                    // Ensure the class has proper imports
                    boolean hasSharedSessionImport = cu.getImports().stream()
                        .anyMatch(imp -> imp.getNameAsString().equals("org.hibernate.engine.spi.SharedSessionContractImplementor"));
                    
                    if (!hasSharedSessionImport) {
                        // Check if we need to add the import
                        NeedsImportCheck visitor = new NeedsImportCheck();
                        cu.accept(visitor, null);
                        if (visitor.getResult()) {
                            cu.addImport("org.hibernate.engine.spi.SharedSessionContractImplementor");
                        }
                    }
                    
                    // Remove old SessionImplementor import if present
                    cu.getImports().removeIf(imp -> imp.getNameAsString().equals("org.hibernate.engine.spi.SessionImplementor"));
                });
            
            try {
                Files.write(javaFile, cu.toString().getBytes());
                return true;
            } catch (Exception e) {
                System.err.println("Error writing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        return false;
    }
    
    private static class NeedsImportCheck extends VoidVisitorAdapter<Void> {
        private boolean needsImport = false;
        
        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            if (n.getNameAsString().equals("SharedSessionContractImplementor")) {
                needsImport = true;
            }
            super.visit(n, arg);
        }
        
        public boolean getResult() {
            return needsImport;
        }
    }
}