package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.configuration.PrettyPrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    
    // Configuration parameters - should be parameterizable for different breaking changes
    private static final String OLD_CLASS_NAME = "com.artipie.asto.factory.Storages";
    private static final String NEW_CLASS_NAME = "com.artipie.asto.factory.StoragesLoader";
    private static final String CONFIG_CLASS_NAME = "com.artipie.asto.factory.Config";
    private static final String OLD_METHOD_NAME = "newStorage";
    private static final String NEW_METHOD_NAME = "newObject";
    private static final String CONFIG_WRAPPER_CLASS = "YamlStorageConfig";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles = findJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(dir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + file)
        );
        
        boolean[] modified = new boolean[]{false};
        
        // Visitor to modify imports
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration importDecl, Void arg) {
                String importName = importDecl.getNameAsString();
                if (importName.equals(OLD_CLASS_NAME)) {
                    modified[0] = true;
                    return new ImportDeclaration(NEW_CLASS_NAME, importDecl.isStatic(), importDecl.isAsterisk());
                }
                return (Node) super.visit(importDecl, arg);
            }
        }, null);
        
        // Check if Config import is needed
        boolean hasConfigImport = cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals(CONFIG_CLASS_NAME));
        
        // Visitor to modify method calls
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(MethodCallExpr methodCall, Void arg) {
                // Check if this is a call to newStorage() on a Storages object
                if (methodCall.getNameAsString().equals(OLD_METHOD_NAME)) {
                    Optional<Expression> scope = methodCall.getScope();
                    if (scope.isPresent() && scope.get() instanceof ObjectCreationExpr) {
                        ObjectCreationExpr objectCreation = (ObjectCreationExpr) scope.get();
                        String typeName = objectCreation.getType().asString();
                        
                        // Check if it's creating a Storages object
                        if (typeName.equals("Storages") || typeName.equals(OLD_CLASS_NAME)) {
                            modified[0] = true;
                            
                            // Change the object creation type to StoragesLoader
                            objectCreation.setType(NEW_CLASS_NAME.substring(NEW_CLASS_NAME.lastIndexOf('.') + 1));
                            
                            // Change method name from newStorage to newObject
                            methodCall.setName(NEW_METHOD_NAME);
                            
                            // Wrap the second argument with Config.YamlStorageConfig
                            NodeList<Expression> arguments = methodCall.getArguments();
                            if (arguments.size() >= 2) {
                                Expression secondArg = arguments.get(1);
                                // Create new Config.YamlStorageConfig(secondArg)
                                ObjectCreationExpr configWrapper = new ObjectCreationExpr();
                                configWrapper.setType("Config." + CONFIG_WRAPPER_CLASS);
                                configWrapper.setArguments(new NodeList<>(secondArg));
                                
                                // Replace the second argument
                                arguments.set(1, configWrapper);
                            }
                        }
                    }
                }
                return (Node) super.visit(methodCall, arg);
            }
        }, null);
        
        if (modified[0] && !hasConfigImport) {
            // Add Config import if we modified the file and Config is not already imported
            cu.addImport(CONFIG_CLASS_NAME);
        }
        
        if (modified[0]) {
            // Write the modified file back
            PrettyPrinterConfiguration config = new PrettyPrinterConfiguration();
            config.setPrintComments(true);
            config.setEndOfLineCharacter("\n");
            PrettyPrinter printer = new PrettyPrinter(config);
            String modifiedCode = printer.print(cu);
            Files.write(file, modifiedCode.getBytes());
            System.out.println("Modified: " + file);
            return true;
        }
        
        return false;
    }
}