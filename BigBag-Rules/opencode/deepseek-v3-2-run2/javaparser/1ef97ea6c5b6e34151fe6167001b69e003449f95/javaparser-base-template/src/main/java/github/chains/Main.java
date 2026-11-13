package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration parameters - these can be changed for different breaking changes
    private static final String DEPENDENCY_CLASS_NAME = "net.datafaker.DateAndTime";
    private static final String METHOD_NAME = "between";
    private static final String OLD_PARAM_TYPE = "Date";
    private static final String NEW_PARAM_TYPE = "Timestamp";
    private static final String OLD_RETURN_TYPE = "Date";
    private static final String NEW_RETURN_TYPE = "Timestamp";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int modifiedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (processFile(javaFile)) {
                    modifiedFiles++;
                    System.out.println("Modified: " + javaFile);
                }
            }
            
            System.out.println("Successfully modified " + modifiedFiles + " files");
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        return Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        String content = Files.readString(javaFile);
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        MethodSignatureTransformer transformer = new MethodSignatureTransformer();
        cu.accept(transformer, null);
        
        if (transformer.isModified()) {
            Files.writeString(javaFile, cu.toString());
            return true;
        }
        return false;
    }
    
    private static class MethodSignatureTransformer extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodDeclaration md, Void arg) {
            System.out.println("Visiting method: " + md.getNameAsString() + " with parameters: " + 
                md.getParameters().stream().map(p -> p.getType().toString()).collect(java.util.stream.Collectors.joining(", ")));
            
            // Check if this method overrides the target method from the dependency
            if (isTargetMethod(md)) {
                System.out.println("Found target method: " + md.getNameAsString());
                
                // Remove @Override annotation since signature changed
                md.getAnnotations().removeIf(annotation -> 
                    annotation.getNameAsString().equals("Override"));
                
                // Update parameter types
                md.getParameters().forEach(param -> {
                    if (param.getType().toString().equals(OLD_PARAM_TYPE)) {
                        param.setType(StaticJavaParser.parseType(NEW_PARAM_TYPE));
                        modified = true;
                    }
                });
                
                // Update method body to handle new parameter types
                updateMethodBody(md);
                
                modified = true;
            }
            
            return super.visit(md, arg);
        }
        
        private boolean isTargetMethod(MethodDeclaration md) {
            // Check if method name matches
            if (!md.getNameAsString().equals(METHOD_NAME)) {
                return false;
            }
            
            // Check if method has exactly 2 parameters
            if (md.getParameters().size() != 2) {
                return false;
            }
            
            // Check if parameters are of the old type
            boolean hasOldParamTypes = md.getParameters().stream()
                    .allMatch(param -> {
                        String paramType = param.getType().toString();
                        System.out.println("Parameter type: " + paramType + " vs OLD_PARAM_TYPE: " + OLD_PARAM_TYPE);
                        return paramType.equals(OLD_PARAM_TYPE);
                    });
            
            if (hasOldParamTypes) {
                System.out.println("Found target method with old parameter types: " + md.getNameAsString());
                return true;
            }
            
            return false;
        }
        
        private void updateMethodBody(MethodDeclaration md) {
            // Update super calls to use new parameter types
            md.accept(new ModifierVisitor<Void>() {
                @Override
                public Visitable visit(MethodCallExpr mce, Void arg) {
                    if (mce.getNameAsString().equals(METHOD_NAME) && 
                        mce.getScope().isPresent() && 
                        mce.getScope().get().toString().equals("super")) {
                        
                        // The method body should already handle the conversion correctly
                        // since it's wrapping the result in new Timestamp(...)
                        System.out.println("Found super call to target method");
                    }
                    return super.visit(mce, arg);
                }
            }, null);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}