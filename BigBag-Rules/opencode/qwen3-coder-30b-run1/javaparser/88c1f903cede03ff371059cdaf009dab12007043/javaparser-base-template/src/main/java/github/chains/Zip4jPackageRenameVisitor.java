package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Zip4jPackageRenameVisitor extends ModifierVisitor<Void> {
    
    @Override
    public Visitable visit(ImportDeclaration importDeclaration, Void arg) {
        // Update import statements from net.lingala.zip4j.core.ZipFile to net.lingala.zip4j.ZipFile
        String importName = importDeclaration.getName().asString();
        if (importName.equals("net.lingala.zip4j.core.ZipFile")) {
            importDeclaration.setName("net.lingala.zip4j.ZipFile");
        }
        return super.visit(importDeclaration, arg);
    }
    
    @Override
    public Visitable visit(ClassExpr classExpr, Void arg) {
        // Update class expressions like ZipFile.class
        if (classExpr.getType().getNameAsString().equals("ZipFile")) {
            // Check if it's a qualified name that needs to be updated
            if (classExpr.getType().getQualifier().isPresent()) {
                String qualifier = classExpr.getType().getQualifier().get().asString();
                if (qualifier.equals("net.lingala.zip4j.core")) {
                    classExpr.setType("net.lingala.zip4j.ZipFile");
                }
            }
        }
        return super.visit(classExpr, arg);
    }
    
    public static void processFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        String content = new String(Files.readAllBytes(path));
        
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Apply the visitor to rename the package
        cu.accept(new Zip4jPackageRenameVisitor(), null);
        
        // Write the updated content back to the file
        Files.write(path, cu.toString().getBytes());
        System.out.println("Updated file: " + filePath);
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Zip4jPackageRenameVisitor <path-to-java-file>");
            return;
        }
        
        try {
            processFile(args[0]);
        } catch (IOException e) {
            System.err.println("Error processing file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}