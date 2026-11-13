package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    
    private static final String OLD_CLASS_NAME = "com.artipie.asto.factory.Storages";
    private static final String NEW_CLASS_NAME = "com.artipie.asto.factory.StoragesLoader";
    private static final String OLD_METHOD_NAME = "newStorage";
    private static final String NEW_METHOD_NAME = "newObject";
    
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            javaFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
                System.out.println("Modified: " + javaFile);
            }
        }
        
        System.out.println("Total files modified: " + modifiedFiles);
    }
    
    private static boolean processFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
        if (cu == null) {
            return false;
        }
        
        boolean[] modified = new boolean[]{false};
        
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Node visit(ImportDeclaration id, Void arg) {
                if (id.getNameAsString().equals(OLD_CLASS_NAME)) {
                    modified[0] = true;
                    return new ImportDeclaration(NEW_CLASS_NAME, id.isStatic(), id.isAsterisk());
                }
                return super.visit(id, arg);
            }
            
            @Override
            public Node visit(CompilationUnit cu, Void arg) {
                Node result = (Node) super.visit(cu, arg);
                // Check if we need to add Config import
                boolean hasConfigImport = false;
                for (ImportDeclaration imp : cu.getImports()) {
                    if (imp.getNameAsString().equals("com.artipie.asto.factory.Config")) {
                        hasConfigImport = true;
                        break;
                    }
                }
                if (!hasConfigImport && modified[0]) {
                    // Check if we have any usage of Config.YamlStorageConfig
                    if (cu.toString().contains("Config.YamlStorageConfig")) {
                        cu.addImport("com.artipie.asto.factory.Config");
                    }
                }
                return result;
            }
            
            @Override
            public Node visit(ObjectCreationExpr expr, Void arg) {
                Node result = (Node) super.visit(expr, arg);
                if (expr.getType().asString().equals("Storages")) {
                    modified[0] = true;
                    expr.setType(NEW_CLASS_NAME.substring(NEW_CLASS_NAME.lastIndexOf('.') + 1));
                }
                return result;
            }
            
            @Override
            public Node visit(MethodCallExpr expr, Void arg) {
                Node result = (Node) super.visit(expr, arg);
                
                if (expr.getNameAsString().equals(OLD_METHOD_NAME)) {
                    if (expr.getScope().isPresent()) {
                        Node scope = expr.getScope().get();
                        boolean isStoragesCall = false;
                        if (scope instanceof NameExpr) {
                            NameExpr nameExpr = (NameExpr) scope;
                            if (nameExpr.getNameAsString().equals("Storages")) {
                                isStoragesCall = true;
                            }
                        } else if (scope instanceof ObjectCreationExpr) {
                            ObjectCreationExpr objExpr = (ObjectCreationExpr) scope;
                            if (objExpr.getType().asString().equals("Storages") || 
                                objExpr.getType().asString().equals("StoragesLoader")) {
                                isStoragesCall = true;
                            }
                        }
                        
                        if (isStoragesCall) {
                            modified[0] = true;
                            expr.setName(NEW_METHOD_NAME);
                            
                            // Check if we need to wrap YamlMapping parameters in Config.YamlStorageConfig
                            if (expr.getArguments().size() >= 2) {
                                // Get the second argument (the config parameter)
                                Expression configArg = expr.getArgument(1);
                                // Check if it's a method call that returns YamlMapping
                                if (configArg instanceof MethodCallExpr) {
                                    MethodCallExpr methodCall = (MethodCallExpr) configArg;
                                    if (methodCall.getNameAsString().equals("readYamlMapping") || 
                                        methodCall.getNameAsString().contains("YamlMapping")) {
                                        // Wrap it in new Config.YamlStorageConfig(...)
                                        ObjectCreationExpr wrappedConfig = new ObjectCreationExpr();
                                        wrappedConfig.setType("Config.YamlStorageConfig");
                                        wrappedConfig.getArguments().add(methodCall);
                                        expr.getArguments().set(1, wrappedConfig);
                                    }
                                }
                            }
                        }
                    }
                }
                
                return result;
            }
        }, null);
        
        if (modified[0]) {
            Files.write(javaFile, cu.toString().getBytes());
            return true;
        }
        
        return false;
    }
}