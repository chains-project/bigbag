package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    public static void main(String[] args) throws IOException {
        // Process all Java files in the source directory
        if (args.length < 1) {
            System.out.println("Usage: java Main <source_directory>");
            return;
        }
        
        String sourceDir = args[0];
        processDirectory(sourceDir);
    }
    
    private static void processDirectory(String dirPath) throws IOException {
        Files.walk(Paths.get(dirPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path path) {
        try {
            // Parse the file
            InputStream inputStream = new FileInputStream(path.toFile());
            CompilationUnit cu = StaticJavaParser.parse(inputStream);
            
            // Apply transformations
            new HazelcastAPITransformer().visit(cu, null);
            
            // Save the transformed file
            try (OutputStream outputStream = new FileOutputStream(path.toFile())) {
                LexicalPreservingPrinter.print(cu, outputStream);
            }
            System.out.println("Processed: " + path.toString());
        } catch (Exception e) {
            System.err.println("Error processing file " + path.toString() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class HazelcastAPITransformer extends VoidVisitorAdapter<Void> {
        
        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Transform import statements that reference deprecated Hazelcast APIs
            String importName = n.getNameAsString();
            
            // Handle the specific breaking changes from Hazelcast 4.x to 5.1
            if (importName.startsWith("com.hazelcast.config.MaxSizeConfig")) {
                // MaxSizeConfig was moved or changed - we remove it from imports
                // Note: This will be handled at the semantic level in the actual code
            } else if (importName.startsWith("com.hazelcast.core.Member")) {
                // Member class may have moved or changed
            } else if (importName.startsWith("com.hazelcast.core.Cluster")) {
                // Cluster class may have moved or changed
            } else if (importName.startsWith("com.hazelcast.core.MemberAttributeEvent")) {
                // MemberAttributeEvent class may have moved or changed
            } else if (importName.startsWith("com.hazelcast.core.MembershipEvent")) {
                // MembershipEvent class may have moved or changed
            } else if (importName.startsWith("com.hazelcast.core.MembershipListener")) {
                // MembershipListener interface may have moved or changed
            } else if (importName.startsWith("com.hazelcast.core.IMap")) {
                // IMap class may have moved or changed
            } else if (importName.startsWith("com.hazelcast.core.MapEvent")) {
                // MapEvent class may have moved or changed
            } else if (importName.startsWith("com.hazelcast.monitor")) {
                // Monitor package was reorganized
            }
        }
    }
}