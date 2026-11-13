package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        // Get the source directory from arguments
        if (args.length == 0) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDirectory = args[0];
        Path dir = Paths.get(sourceDirectory);
        
        // Process all Java files
        try (Stream<Path> paths = Files.walk(dir).filter(p -> p.toString().endsWith(".java"))) {
            List<Path> javaFiles = paths.collect(Collectors.toList());
            
            for (Path file : javaFiles) {
                processFile(file);
            }
        }
        
        System.out.println("Transformation complete!");
    }
    
    private static void processFile(Path file) throws IOException {
        try (FileInputStream in = new FileInputStream(file.toFile())) {
            CompilationUnit cu = StaticJavaParser.parse(in);
            
            // Find and transform imports
            boolean modified = false;
            NodeList<ImportDeclaration> imports = cu.getImports();
            
            for (ImportDeclaration imp : imports) {
                Name name = imp.getName();
                if (name.asString().equals("net.lingala.zip4j.core.ZipFile")) {
                    // Replace with new import
                    imp.setName(new Name("net.lingala.zip4j.ZipFile"));
                    modified = true;
                }
            }
            
            if (modified) {
                try (FileWriter writer = new FileWriter(file.toFile())) {
                    writer.write(cu.toString());
                }
            }
        }
    }
}
