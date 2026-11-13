package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("This tool fixes the breaking change: eu.europa.esig.dss.pades.CertificationPermission -> eu.europa.esig.dss.enumerations.CertificationPermission");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source files in: " + sourceDir);
        System.out.println("Fixing: eu.europa.esig.dss.pades.CertificationPermission -> eu.europa.esig.dss.enumerations.CertificationPermission");
        
        try {
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int transformedFiles = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws Exception {
        return Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
    }
    
    private static boolean transformFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                () -> new RuntimeException("Failed to parse: " + javaFile)
            );
            
            CertificationPermissionVisitor visitor = new CertificationPermissionVisitor();
            CompilationUnit transformedCu = (CompilationUnit) cu.accept(visitor, null);
            
            if (visitor.wasTransformed()) {
                transformedCu = fixDuplicateImports(transformedCu);
                Files.write(javaFile, transformedCu.toString().getBytes());
                System.out.println("Transformed: " + javaFile);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            return false;
        }
    }
    
    private static CompilationUnit fixDuplicateImports(CompilationUnit cu) {
        NodeList<ImportDeclaration> imports = cu.getImports();
        List<ImportDeclaration> uniqueImports = new ArrayList<>();
        
        for (ImportDeclaration imp : imports) {
            boolean isDuplicate = uniqueImports.stream()
                .anyMatch(existing -> existing.getNameAsString().equals(imp.getNameAsString()));
            
            if (!isDuplicate) {
                uniqueImports.add(imp);
            }
        }
        
        cu.setImports(new NodeList<>(uniqueImports.toArray(new ImportDeclaration[0])));
        return cu;
    }
    
    static class CertificationPermissionVisitor extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public com.github.javaparser.ast.Node visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            if (importName.equals("eu.europa.esig.dss.pades.CertificationPermission")) {
                transformed = true;
                importDecl.setName("eu.europa.esig.dss.enumerations.CertificationPermission");
                System.out.println("  Updated import: " + importName + " -> " + importDecl.getNameAsString());
            }
            
            return super.visit(importDecl, arg);
        }
    }
}