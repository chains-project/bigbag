package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
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
    private static final String OLD_CLASS_NAME = "TranslationsResource";
    private static final String NEW_CLASS_NAME = "Translation";
    private static final String OLD_PACKAGE = "com.google.api.services.translate.model";
    private static final String NEW_PACKAGE = "com.google.api.services.translate.v3.model";
    private static final String OLD_METHOD_NAME = "getDetectedSourceLanguage";
    private static final String NEW_METHOD_NAME = "getDetectedLanguageCode";
    
    // Additional class mappings for the breaking change
    private static final String[][] CLASS_MAPPINGS = {
        {"TranslationsResource", "Translation"},
        {"LanguagesResource", "SupportedLanguage"},
        {"DetectionsResourceItems", "DetectedLanguage"}
    };
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = Files.walk(sourcePath)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Successfully modified " + modifiedFiles + " files");
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        
        // Skip files that don't contain the old class reference
        if (!content.contains(OLD_CLASS_NAME) && !content.contains(OLD_METHOD_NAME)) {
            return false;
        }
        
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            for (String[] mapping : CLASS_MAPPINGS) {
                String oldClass = mapping[0];
                String newClass = mapping[1];
                if (importName.equals(OLD_PACKAGE + "." + oldClass)) {
                    importDecl.setName(NEW_PACKAGE + "." + newClass);
                    modified = true;
                }
            }
            if (importName.equals(OLD_PACKAGE)) {
                // If importing the entire old package, we need to add the new package imports
                for (String[] mapping : CLASS_MAPPINGS) {
                    String newClass = mapping[1];
                    cu.addImport(NEW_PACKAGE + "." + newClass);
                }
                modified = true;
            }
        }
        
        // Transform class references and method calls
        boolean[] visitorModified = new boolean[]{false};
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ClassOrInterfaceType n, Void arg) {
                for (String[] mapping : CLASS_MAPPINGS) {
                    String oldClass = mapping[0];
                    String newClass = mapping[1];
                    if (n.getNameAsString().equals(oldClass)) {
                        visitorModified[0] = true;
                        return new ClassOrInterfaceType(null, newClass);
                    }
                }
                return super.visit(n, arg);
            }
            
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals(OLD_METHOD_NAME)) {
                    n.setName(new SimpleName(NEW_METHOD_NAME));
                    visitorModified[0] = true;
                }
                return super.visit(n, arg);
            }
        }, null);
        
        modified = modified || visitorModified[0];
        
        if (modified) {
            Files.writeString(filePath, cu.toString());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
}