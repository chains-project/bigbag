package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.util.List;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        List<Path> javaFiles = findJavaFiles(sourceDir);
        
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int filesModified = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                filesModified++;
                System.out.println("Modified: " + javaFile);
            }
        }
        
        System.out.println("Total files modified: " + filesModified);
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            cu = parser.parse(in).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + javaFile));
        }
        
        boolean[] modified = new boolean[]{false};
        
        // Visitor for javax.servlet -> jakarta.servlet imports
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration id, Void arg) {
                String importName = id.getNameAsString();
                
                // Replace javax.servlet with jakarta.servlet
                if (importName.startsWith("javax.servlet")) {
                    String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                    modified[0] = true;
                    return new ImportDeclaration(newImport, id.isStatic(), id.isAsterisk());
                }
                
                // Replace SelectChannelConnector import
                if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                    modified[0] = true;
                    return new ImportDeclaration("org.eclipse.jetty.server.ServerConnector", 
                                                id.isStatic(), id.isAsterisk());
                }
                
                return (Node) super.visit(id, arg);
            }
        }, null);
        
        // Visitor for SelectChannelConnector constructor calls
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ObjectCreationExpr expr, Void arg) {
                if (expr.getType().asString().equals("SelectChannelConnector")) {
                    modified[0] = true;
                    expr.setType(new ClassOrInterfaceType(null, "ServerConnector"));
                    // ServerConnector requires Server parameter
                    if (expr.getArguments().isEmpty()) {
                        // Add server parameter - we assume there's a 'server' variable in scope
                        // This is a common pattern when migrating from SelectChannelConnector
                        expr.getArguments().add(new NameExpr("server"));
                    }
                }
                return (Node) super.visit(expr, arg);
            }
        }, null);
        
        // Visitor for SelectChannelConnector type references
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ClassOrInterfaceType type, Void arg) {
                if (type.getNameAsString().equals("SelectChannelConnector")) {
                    modified[0] = true;
                    type.setName("ServerConnector");
                }
                return (Node) super.visit(type, arg);
            }
        }, null);
        
        // Also update comments
        List<Comment> comments = cu.getAllContainedComments();
        for (Comment comment : comments) {
            String content = comment.getContent();
            if (content.contains("javax.servlet")) {
                modified[0] = true;
                comment.setContent(content.replace("javax.servlet", "jakarta.servlet"));
            }
            if (content.contains("SelectChannelConnector")) {
                modified[0] = true;
                comment.setContent(content.replace("SelectChannelConnector", "ServerConnector"));
            }
        }
        
        if (modified[0]) {
            // Write back the modified file
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String newContent = printer.print(cu);
            
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(newContent);
            }
        }
        
        return modified[0];
    }
}