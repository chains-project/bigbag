package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.InstanceOfExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.imports.ImportDeclaration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        processDirectory(new File(sourceDir));
    }
    
    private static void processDirectory(File dir) throws IOException {
        if (!dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    processDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    processJavaFile(file);
                }
            }
        }
    }
    
    private static void processJavaFile(File javaFile) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(javaFile.getAbsolutePath())));
        CompilationUnit cu = StaticJavaParser.parse(content);
        
        // Check if this file imports Xpp3Dom
        boolean hasXpp3DomImport = cu.getImports().stream()
                .anyMatch(importDecl -> importDecl.getNameAsString().contains("Xpp3Dom"));
        
        if (hasXpp3DomImport) {
            // Remove Xpp3Dom imports
            cu.getImports().removeIf(importDecl -> 
                importDecl.getNameAsString().contains("Xpp3Dom")
            );
            
            // Add standard XML imports
            cu.addImport("org.w3c.dom.Document");
            cu.addImport("org.w3c.dom.Element");
            cu.addImport("org.w3c.dom.NodeList");
            cu.addImport("javax.xml.parsers.DocumentBuilder");
            cu.addImport("javax.xml.parsers.DocumentBuilderFactory");
            cu.addImport("javax.xml.parsers.ParserConfigurationException");
            
            // Replace Xpp3Dom type references in instanceof expressions
            cu.findAll(InstanceOfExpr.class).forEach(instanceOf -> {
                if (instanceOf.getType().toString().contains("Xpp3Dom")) {
                    instanceOf.setType("Document");
                }
            });
            
            // Replace Xpp3Dom object creation expressions
            cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
                if (creation.getType().toString().contains("Xpp3Dom")) {
                    // Replace with Document creation
                    creation.setType("DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()");
                }
            });
            
            // Write back the modified content
            String newContent = cu.toString();
            Files.write(Paths.get(javaFile.getAbsolutePath()), newContent.getBytes());
            
            System.out.println("Updated file: " + javaFile.getAbsolutePath());
        }
    }
}