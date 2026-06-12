package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
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
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            List<Path> javaFiles = paths
                .filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            for (Path filePath : javaFiles) {
                fixEncoderFooterBytes(filePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void fixEncoderFooterBytes(Path filePath) {
        try {
            FileInputStream in = new FileInputStream(filePath.toFile());
            CompilationUnit cu = StaticJavaParser.parse(in);
            in.close();
            
            // Visit all class declarations
            cu.accept(new EncoderVisitor(), null);
            
            // Write back to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(cu.toString());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class EncoderVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);
            
            // Check if this class extends EncoderBase
            Optional<ClassOrInterfaceType> superClass = classDecl.getExtendedTypes()
                .stream()
                .filter(type -> type.getNameAsString().contains("EncoderBase"))
                .findFirst();
            
            if (superClass.isPresent()) {
                // Check if it implements footerBytes method
                boolean hasFooterBytes = classDecl.getMethods()
                    .stream()
                    .anyMatch(method -> method.getNameAsString().equals("footerBytes"));
                
                if (!hasFooterBytes) {
                    // Add footerBytes method
                    MethodDeclaration footerBytesMethod = new MethodDeclaration();
                    footerBytesMethod.setName("footerBytes");
                    footerBytesMethod.setType("byte[]");
                    footerBytesMethod.setPublic(true);
                    footerBytesMethod.setBody(new BlockStmt().addStatement(new ReturnStmt().setExpression(null)));
                    
                    classDecl.addMethod(footerBytesMethod);
                }
            }
        }
    }
}
