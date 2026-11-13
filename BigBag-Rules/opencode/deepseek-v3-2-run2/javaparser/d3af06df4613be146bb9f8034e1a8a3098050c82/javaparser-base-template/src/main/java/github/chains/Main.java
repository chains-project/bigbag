package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming servlet imports from javax.* to jakarta.* in: " + sourceDir);
        
        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        int transformedFiles = 0;
        int totalTransformations = 0;
        
        JavaParser parser = new JavaParser();
        
        for (Path javaFile : javaFiles) {
            try {
                ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
                if (!parseResult.isSuccessful()) {
                    System.err.println("Error parsing file: " + javaFile);
                    continue;
                }
                
                Optional<CompilationUnit> cuOpt = parseResult.getResult();
                if (!cuOpt.isPresent()) {
                    System.err.println("No compilation unit for file: " + javaFile);
                    continue;
                }
                
                CompilationUnit cu = cuOpt.get();
                ServletImportTransformer transformer = new ServletImportTransformer();
                cu.accept(transformer, null);
                int changes = transformer.getTransformationCount();
                
                if (changes > 0) {
                    totalTransformations += changes;
                    transformedFiles++;
                    
                    try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                        writer.write(cu.toString());
                    }
                    
                    System.out.println("Transformed " + changes + " occurrences in: " + sourceDir.relativize(javaFile));
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("\nSummary:");
        System.out.println("  Files processed: " + javaFiles.size());
        System.out.println("  Files transformed: " + transformedFiles);
        System.out.println("  Total transformations: " + totalTransformations);
    }
    
    static class ServletImportTransformer extends GenericVisitorAdapter<Void, Void> {
        private int transformationCount = 0;
        
        public int getTransformationCount() {
            return transformationCount;
        }
        
        @Override
        public Void visit(ImportDeclaration importDecl, Void arg) {
            String importName = importDecl.getNameAsString();
            
            if (importName.startsWith("javax.servlet")) {
                String newImportName = importName.replaceFirst("^javax\\.servlet", "jakarta.servlet");
                importDecl.setName(newImportName);
                transformationCount++;
                System.out.println("  Import: " + importName + " -> " + newImportName);
            }
            
            return super.visit(importDecl, arg);
        }
        
        @Override
        public Void visit(ClassOrInterfaceType type, Void arg) {
            String typeName = type.getNameAsString();
            
            if (typeName.startsWith("javax.servlet.")) {
                String newTypeName = typeName.replaceFirst("^javax\\.servlet\\.", "jakarta.servlet.");
                type.setName(newTypeName);
                transformationCount++;
            }
            
            if (type.getScope().isPresent()) {
                ClassOrInterfaceType scope = type.getScope().get();
                String scopeName = scope.asString();
                if (scopeName.startsWith("javax.servlet.")) {
                    String newScopeName = scopeName.replaceFirst("^javax\\.servlet\\.", "jakarta.servlet.");
                    scope.setName(newScopeName);
                    transformationCount++;
                }
            }
            
            return super.visit(type, arg);
        }
        
        @Override
        public Void visit(NameExpr nameExpr, Void arg) {
            String name = nameExpr.getNameAsString();
            
            if (name.startsWith("javax.servlet.")) {
                String newName = name.replaceFirst("^javax\\.servlet\\.", "jakarta.servlet.");
                nameExpr.setName(newName);
                transformationCount++;
            }
            
            return super.visit(nameExpr, arg);
        }
    }
}