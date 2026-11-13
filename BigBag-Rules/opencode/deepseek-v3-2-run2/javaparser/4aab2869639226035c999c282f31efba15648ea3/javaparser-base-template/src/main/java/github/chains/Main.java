package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic transformation rule for asto-core v1.15.3 breaking API change:
 * - Old: com.artipie.asto.factory.Storages.newStorage(String, YamlMapping)
 * - New: com.artipie.asto.factory.StoragesLoader.newObject(String, Config)
 * 
 * This transformation:
 * 1. Replaces imports from Storages to StoragesLoader
 * 2. Replaces new Storages() with new StoragesLoader()
 * 3. Replaces .newStorage(type, config) with .newObject(type, new YamlStorageConfig(config))
 */
public class Main {
    
    private static final String OLD_CLASS_NAME = "com.artipie.asto.factory.Storages";
    private static final String NEW_CLASS_NAME = "com.artipie.asto.factory.StoragesLoader";
    private static final String CONFIG_CLASS_NAME = "com.artipie.asto.factory.Config$YamlStorageConfig";
    private static final String OLD_METHOD_NAME = "newStorage";
    private static final String NEW_METHOD_NAME = "newObject";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            JavaParser parser = new JavaParser(new ParserConfiguration());
            CompilationUnit cu = parser.parse(content).getResult().orElseThrow();
            
            boolean modified = transformCompilationUnit(cu);
            
            if (modified) {
                Files.writeString(filePath, cu.toString());
                System.out.println("Modified: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        StoragesTransformationVisitor visitor = new StoragesTransformationVisitor();
        CompilationUnit transformed = (CompilationUnit) cu.accept(visitor, null);
        return visitor.isModified();
    }
    
    private static class StoragesTransformationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Node visit(ImportDeclaration importDecl, Void arg) {
            if (importDecl.getNameAsString().equals(OLD_CLASS_NAME)) {
                importDecl.setName(NEW_CLASS_NAME);
                modified = true;
            }
            return (Node) super.visit(importDecl, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr expr, Void arg) {
            if (expr.getType().asString().equals("Storages")) {
                expr.setType("StoragesLoader");
                modified = true;
            }
            return (Node) super.visit(expr, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr expr, Void arg) {
            if (expr.getNameAsString().equals(OLD_METHOD_NAME)) {
                // Check if this is a call on a Storages instance
                if (expr.getScope().isPresent()) {
                    Expression scope = expr.getScope().get();
                    // Handle both Storages.newStorage() and new Storages().newStorage() patterns
                    boolean isStoragesCall = false;
                    
                    if (scope instanceof NameExpr && ((NameExpr) scope).getNameAsString().equals("Storages")) {
                        isStoragesCall = true;
                    } else if (scope instanceof ObjectCreationExpr) {
                        ObjectCreationExpr creationExpr = (ObjectCreationExpr) scope;
                        if (creationExpr.getType().asString().equals("Storages")) {
                            isStoragesCall = true;
                        }
                    }
                    
                    if (isStoragesCall) {
                        // Replace with StoragesLoader.newObject(type, new YamlStorageConfig(config))
                        expr.setName(NEW_METHOD_NAME);
                        
                        // Get the arguments
                        NodeList<Expression> arguments = expr.getArguments();
                        if (arguments.size() >= 2) {
                            // Replace second argument with new YamlStorageConfig(config)
                            Expression configArg = arguments.get(1);
                            ObjectCreationExpr newConfig = new ObjectCreationExpr();
                            newConfig.setType(CONFIG_CLASS_NAME);
                            newConfig.addArgument(configArg);
                            arguments.set(1, newConfig);
                        }
                        modified = true;
                    }
                }
            }
            return (Node) super.visit(expr, arg);
        }
    }
}