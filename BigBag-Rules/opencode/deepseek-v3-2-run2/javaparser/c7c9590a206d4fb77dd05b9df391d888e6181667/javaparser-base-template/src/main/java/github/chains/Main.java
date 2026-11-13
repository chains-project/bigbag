package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration for the breaking change
    private static final String OLD_IMPORT_PATH = "org.apache.maven.doxia.module.xhtml.decoration.render.RenderingContext";
    private static final String NEW_IMPORT_PATH = "org.apache.maven.doxia.siterenderer.RenderingContext";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp target/classes github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files to process");
            
            int totalTransformed = 0;
            for (Path javaFile : javaFiles) {
                if (transformFile(javaFile)) {
                    totalTransformed++;
                }
            }
            
            System.out.println("Transformed " + totalTransformed + " files successfully");
            
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean transformFile(Path javaFile) throws IOException {
        String content = new String(Files.readAllBytes(javaFile));
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElse(null);
        
        if (cu == null) {
            System.err.println("Failed to parse: " + javaFile);
            return false;
        }
        
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.equals(OLD_IMPORT_PATH)) {
                System.out.println("  Replacing import: " + OLD_IMPORT_PATH + " -> " + NEW_IMPORT_PATH);
                importDecl.setName(NEW_IMPORT_PATH);
                modified = true;
            }
        }
        
        // Find and transform all ObjectCreationExpr nodes
        cu.findAll(ObjectCreationExpr.class).forEach(expr -> {
            // Check if this is a RenderingContext constructor call
            String typeName = expr.getType().asString();
            
            if ("RenderingContext".equals(typeName)) {
                NodeList<com.github.javaparser.ast.expr.Expression> arguments = expr.getArguments();
                
                // Check for 2-argument constructor: RenderingContext(File, String)
                if (arguments.size() == 2) {
                    System.out.println("  Found 2-argument RenderingContext constructor, adding generator argument");
                    
                    // Create new arguments: add empty string as generator parameter
                    NodeList<com.github.javaparser.ast.expr.Expression> newArguments = new NodeList<>();
                    newArguments.add(arguments.get(0)); // File basedir
                    newArguments.add(arguments.get(1)); // String document
                    newArguments.add(new StringLiteralExpr("")); // String generator
                    
                    expr.setArguments(newArguments);
                }
            }
        });
        
        if (modified) {
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
            System.out.println("Transformed: " + javaFile);
            return true;
        }
        
        return false;
    }
}