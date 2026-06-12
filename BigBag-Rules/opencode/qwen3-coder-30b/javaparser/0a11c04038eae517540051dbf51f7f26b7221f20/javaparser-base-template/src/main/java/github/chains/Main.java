package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: Main <source_directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        Path sourcePath = Paths.get(sourceDir);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        // Process all Java files in the directory
        Files.walk(sourcePath)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processJavaFile);
    }
    
    private static void processJavaFile(Path filePath) {
        try {
            // Parse the Java file
            CompilationUnit cu = StaticJavaParser.parse(filePath.toFile());
            
            // Find all Constructor calls with a Class or String parameter that need to be updated
            boolean modified = false;
            
            // Pattern 1: new Constructor(Class)
            modified |= replaceConstructorWithClassCall(cu);
            
            // Pattern 2: new Constructor(String)
            modified |= replaceConstructorWithStringCall(cu);
            
            // Pattern 3: new Constructor(TypeDescription, Collection)
            modified |= replaceConstructorWithDescriptionAndCollectionCall(cu);
            
            // Pattern 4: new Constructor(TypeDescription)
            modified |= replaceConstructorWithDescriptionCall(cu);
            
            if (modified) {
                // Save the modified file
                try (FileWriter writer = new FileWriter(filePath.toFile())) {
                    writer.write(cu.toString());
                }
                System.out.println("Modified: " + filePath);
            }
        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean replaceConstructorWithClassCall(CompilationUnit cu) {
        boolean modified = false;
        
        // Find Constructor calls with a single Class argument (new Constructor(Class))
        List<ObjectCreationExpr> objectCreationExprs = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr expr : objectCreationExprs) {
            if (expr.getType().asString().contains("Constructor")) {
                NodeList<Expression> args = expr.getArguments();
                if (args.size() == 1 && args.get(0) instanceof ClassExpr) {
                    // Add LoaderOptions parameter to the constructor call
                    expr.getArguments().add(new ObjectCreationExpr(null, 
                        new ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"), 
                        NodeList.nodeList()));
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    private static boolean replaceConstructorWithStringCall(CompilationUnit cu) {
        boolean modified = false;
        
        // Find Constructor calls with a String argument (new Constructor(String))
        List<ObjectCreationExpr> objectCreationExprs = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr expr : objectCreationExprs) {
            if (expr.getType().asString().contains("Constructor")) {
                NodeList<Expression> args = expr.getArguments();
                if (args.size() == 1 && args.get(0) instanceof StringLiteralExpr) {
                    // Add LoaderOptions parameter to the constructor call
                    expr.getArguments().add(new ObjectCreationExpr(null, 
                        new ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"), 
                        NodeList.nodeList()));
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    private static boolean replaceConstructorWithDescriptionAndCollectionCall(CompilationUnit cu) {
        boolean modified = false;
        
        // Find Constructor calls with TypeDescription and Collection arguments
        List<ObjectCreationExpr> objectCreationExprs = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr expr : objectCreationExprs) {
            if (expr.getType().asString().contains("Constructor")) {
                NodeList<Expression> args = expr.getArguments();
                if (args.size() == 3) {
                    // Add LoaderOptions parameter to the constructor call
                    expr.getArguments().add(new ObjectCreationExpr(null, 
                        new ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"), 
                        NodeList.nodeList()));
                    modified = true;
                }
            }
        }
        
        return modified;
    }
    
    private static boolean replaceConstructorWithDescriptionCall(CompilationUnit cu) {
        boolean modified = false;
        
        // Find Constructor calls with TypeDescription argument
        List<ObjectCreationExpr> objectCreationExprs = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr expr : objectCreationExprs) {
            if (expr.getType().asString().contains("Constructor")) {
                NodeList<Expression> args = expr.getArguments();
                if (args.size() == 2) {
                    // Add LoaderOptions parameter to the constructor call
                    expr.getArguments().add(new ObjectCreationExpr(null, 
                        new ClassOrInterfaceType(null, "org.yaml.snakeyaml.LoaderOptions"), 
                        NodeList.nodeList()));
                    modified = true;
                }
            }
        }
        
        return modified;
    }
}