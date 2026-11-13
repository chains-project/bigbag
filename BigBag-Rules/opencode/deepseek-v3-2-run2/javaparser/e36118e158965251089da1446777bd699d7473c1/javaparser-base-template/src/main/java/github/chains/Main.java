package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
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
        
        Path sourceDir = Paths.get(args[0]);
        List<Path> javaFiles = findJavaFiles(sourceDir);
        
        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
        
        System.out.println("Transformation completed on " + javaFiles.size() + " files");
    }
    
    private static List<Path> findJavaFiles(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static void transformFile(Path file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
        
        boolean modified = false;
        
        // Remove org.cactoos.map.MapEntry imports
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals("org.cactoos.map.MapEntry")) {
                importsToRemove.add(imp);
                modified = true;
            }
        }
        cu.getImports().removeAll(importsToRemove);
        
        // Add import for java.util.AbstractMap if not already present
        boolean hasAbstractMapImport = false;
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals("java.util.AbstractMap")) {
                hasAbstractMapImport = true;
                break;
            }
        }
        
        if (modified && !hasAbstractMapImport) {
            cu.addImport("java.util.AbstractMap");
        }
        
        // Transform MapEntry constructor calls to AbstractMap.SimpleEntry
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(ObjectCreationExpr n, Void arg) {
                // Check if this is a MapEntry constructor
                if (n.getType().isClassOrInterfaceType()) {
                    ClassOrInterfaceType type = n.getType().asClassOrInterfaceType();
                    String typeName = type.getNameAsString();
                    
                    // Check for MapEntry (could be simple name or fully qualified)
                    if (typeName.equals("MapEntry")) {
                        // Replace MapEntry constructor with AbstractMap.SimpleEntry
                        ClassOrInterfaceType newType = StaticJavaParser.parseClassOrInterfaceType("AbstractMap.SimpleEntry");
                        ObjectCreationExpr newExpr = new ObjectCreationExpr(
                            null,
                            newType,
                            n.getTypeArguments().orElse(new NodeList<>()),
                            n.getArguments(),
                            null
                        );
                        return newExpr;
                    }
                }
                
                return super.visit(n, arg);
            }
            
            @Override
            public Visitable visit(ClassOrInterfaceType n, Void arg) {
                // Also transform MapEntry type references to Map.Entry
                if (n.getNameAsString().equals("MapEntry")) {
                    // Replace MapEntry type with java.util.Map.Entry
                    ClassOrInterfaceType newType = StaticJavaParser.parseClassOrInterfaceType("Map.Entry");
                    // Preserve type arguments if any
                    if (n.getTypeArguments().isPresent()) {
                        newType.setTypeArguments(n.getTypeArguments().get());
                    }
                    return newType;
                }
                
                return super.visit(n, arg);
            }
        }, null);
        
        if (modified) {
            Files.write(file, cu.toString().getBytes());
        }
    }
}