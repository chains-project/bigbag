package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar <jar-file> <source-directory>");
            return;
        }
        
        String sourceDirectory = args[0];
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDirectory))) {
            List<Path> javaFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());

            for (Path javaFilePath : javaFiles) {
                processJavaFile(javaFilePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
        }
    }
    
    private static void processJavaFile(Path javaFilePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFilePath.toFile());
            
            // Create a visitor to find @Mapper annotations
            MapperAnnotationVisitor visitor = new MapperAnnotationVisitor();
            visitor.visit(cu, null);
            
        } catch (Exception e) {
            System.err.println("Error processing " + javaFilePath + ": " + e.getMessage());
        }
    }
    
    private static class MapperAnnotationVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);
            
            // Check if this is a Mapper interface
            Optional<AnnotationExpr> mapperAnnotation = classDecl.getAnnotationByName("Mapper");
            
            if (mapperAnnotation.isPresent()) {
                // This is a @Mapper annotated interface
                // Report the issue for MapStruct 1.5.0.Final
                System.out.println("Found @Mapper annotation in " + classDecl.getNameAsString() + 
                    " at " + classDecl.getBegin().get().line + " line");
            }
        }
    }
}