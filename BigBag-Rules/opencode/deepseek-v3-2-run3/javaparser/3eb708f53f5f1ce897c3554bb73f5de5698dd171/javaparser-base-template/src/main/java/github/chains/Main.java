package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration for the breaking API change
    private static final class ApiChangeConfig {
        final String oldPackage;
        final String newPackage;
        final String oldClassName;
        final String newClassName;
        final List<MethodMapping> methodMappings;
        
        ApiChangeConfig(String oldPackage, String newPackage, 
                       String oldClassName, String newClassName,
                       List<MethodMapping> methodMappings) {
            this.oldPackage = oldPackage;
            this.newPackage = newPackage;
            this.oldClassName = oldClassName;
            this.newClassName = newClassName;
            this.methodMappings = methodMappings;
        }
    }
    
    private static final class MethodMapping {
        final String oldMethodName;
        final String newMethodName;
        
        MethodMapping(String oldMethodName, String newMethodName) {
            this.oldMethodName = oldMethodName;
            this.newMethodName = newMethodName;
        }
    }
    
    // Define all the API changes
    private static final List<ApiChangeConfig> API_CHANGES = List.of(
        // TranslationsResource -> Translation
        new ApiChangeConfig(
            "com.google.api.services.translate.model",
            "com.google.api.services.translate.v3.model",
            "TranslationsResource",
            "Translation",
            List.of(
                new MethodMapping("getTranslatedText", "getTranslatedText"),
                new MethodMapping("getDetectedSourceLanguage", "getDetectedLanguageCode"),
                new MethodMapping("getModel", "getModel")
            )
        ),
        // LanguagesResource -> SupportedLanguage
        new ApiChangeConfig(
            "com.google.api.services.translate.model",
            "com.google.api.services.translate.v3.model",
            "LanguagesResource",
            "SupportedLanguage",
            List.of(
                new MethodMapping("getLanguage", "getLanguageCode"),
                new MethodMapping("getName", "getDisplayName")
            )
        ),
        // DetectionsResourceItems -> DetectedLanguage
        new ApiChangeConfig(
            "com.google.api.services.translate.model",
            "com.google.api.services.translate.v3.model",
            "DetectionsResourceItems",
            "DetectedLanguage",
            List.of(
                new MethodMapping("getLanguage", "getLanguageCode"),
                new MethodMapping("getConfidence", "getConfidence")
            )
        ),
        // Translate -> Translate (v3)
        new ApiChangeConfig(
            "com.google.api.services.translate",
            "com.google.api.services.translate.v3",
            "Translate",
            "Translate",
            List.of()
        )
    );
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying API migration transformations to: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (applyTransformations(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Transformation complete. Modified " + transformedFiles + " files.");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static boolean applyTransformations(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new IOException("Failed to parse: " + javaFile)
        );
        
        boolean modified = false;
        
        // Apply each API change transformation
        for (ApiChangeConfig config : API_CHANGES) {
            ApiChangeVisitor visitor = new ApiChangeVisitor(config);
            visitor.visit(cu, null);
            if (visitor.wasModified()) {
                modified = true;
            }
        }
        
        if (modified) {
            // Write back the transformed file
            Files.write(javaFile, cu.toString().getBytes());
            System.out.println("Modified: " + javaFile);
            return true;
        }
        
        return false;
    }
    
    private static class ApiChangeVisitor extends ModifierVisitor<Void> {
        private final ApiChangeConfig config;
        private boolean modified = false;
        
        ApiChangeVisitor(ApiChangeConfig config) {
            this.config = config;
        }
        
        boolean wasModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            // Check if this import matches our old class
            if (importName.equals(config.oldPackage + "." + config.oldClassName) ||
                importName.endsWith("." + config.oldClassName)) {
                
                // Replace with new import
                String newImportName;
                if (importName.startsWith(config.oldPackage)) {
                    newImportName = importName.replace(config.oldPackage, config.newPackage)
                                            .replace(config.oldClassName, config.newClassName);
                } else {
                    newImportName = config.newPackage + "." + config.newClassName;
                }
                
                modified = true;
                importDecl.setName(newImportName);
            }
            
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            // Check if this type reference matches our old class
            if (typeName.equals(config.oldClassName)) {
                modified = true;
                type.setName(config.newClassName);
            }
            
            return (Node) super.visit(type, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr methodCall, Void arg) {
            // Check if this is a method call on our old class type
            // We need to check the scope of the method call
            if (methodCall.getScope().isPresent()) {
                // For simplicity, we'll check if the method name matches old method names
                // A more sophisticated solution would track variable types
                String methodName = methodCall.getNameAsString();
                
                for (MethodMapping mapping : config.methodMappings) {
                    if (methodName.equals(mapping.oldMethodName) && 
                        !methodName.equals(mapping.newMethodName)) {
                        
                        // Rename the method
                        modified = true;
                        methodCall.setName(mapping.newMethodName);
                    }
                }
            }
            
            return (Node) super.visit(methodCall, arg);
        }
    }
}