package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findAllJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int transformedMethods = 0;
            
            for (Path javaFile : javaFiles) {
                boolean fileModified = transformFile(javaFile);
                if (fileModified) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformed " + transformedMethods + " methods in " + transformedFiles + " files");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<Path> findAllJavaFiles(Path directory) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        String content = Files.readString(javaFile);
        
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        if (cu == null) {
            return false;
        }
        
        UserTypeMethodTransformer transformer = new UserTypeMethodTransformer();
        transformer.visit(cu, null);
        
        if (transformer.isModified()) {
            Files.writeString(javaFile, cu.toString());
            return true;
        }
        
        return false;
    }
    
    private static class UserTypeMethodTransformer extends VoidVisitorAdapter<Void> {
        private boolean modified = false;
        private boolean needsSharedSessionContractImport = false;
        
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            
            // Check if this method overrides nullSafeGet or nullSafeSet from UserType
            if (isNullSafeGetOrSetMethod(md)) {
                NodeList<Parameter> parameters = md.getParameters();
                boolean methodModified = false;
                
                // Transform parameters: change SessionImplementor to SharedSessionContractImplementor
                for (Parameter param : parameters) {
                    if (param.getType().toString().equals("SessionImplementor")) {
                        // Change type to SharedSessionContractImplementor
                        param.setType(new ClassOrInterfaceType(null, "SharedSessionContractImplementor"));
                        
                        // Optionally update parameter name if it's sessionImplementor
                        if (param.getNameAsString().equals("sessionImplementor")) {
                            param.setName("session");
                        }
                        
                        methodModified = true;
                        needsSharedSessionContractImport = true;
                        System.out.println("Transformed parameter in method: " + md.getNameAsString());
                    }
                }
                
                if (methodModified) {
                    modified = true;
                }
            }
        }
        
        @Override
        public void visit(CompilationUnit cu, Void arg) {
            super.visit(cu, arg);
            
            // After visiting all nodes, check if we need to update imports
            if (needsSharedSessionContractImport) {
                // Check if SharedSessionContractImplementor import already exists
                boolean hasSharedSessionImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("org.hibernate.engine.spi.SharedSessionContractImplementor"));
                
                // Check if SessionImplementor import exists
                boolean hasSessionImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals("org.hibernate.engine.spi.SessionImplementor"));
                
                // Add SharedSessionContractImplementor import if needed
                if (!hasSharedSessionImport) {
                    cu.addImport("org.hibernate.engine.spi.SharedSessionContractImplementor");
                    System.out.println("Added import for SharedSessionContractImplementor");
                }
                
                // Remove SessionImplementor import if it exists
                if (hasSessionImport) {
                    cu.getImports().removeIf(imp -> imp.getNameAsString().equals("org.hibernate.engine.spi.SessionImplementor"));
                    System.out.println("Removed import for SessionImplementor");
                }
            }
        }
        
        private boolean isNullSafeGetOrSetMethod(MethodDeclaration md) {
            String methodName = md.getNameAsString();
            return methodName.equals("nullSafeGet") || methodName.equals("nullSafeSet");
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}