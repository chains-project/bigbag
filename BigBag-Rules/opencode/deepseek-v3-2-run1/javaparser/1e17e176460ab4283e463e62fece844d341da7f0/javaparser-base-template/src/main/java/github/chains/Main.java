package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar <jar> <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<File> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(path -> javaFiles.add(path.toFile()));
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int modifiedFiles = 0;
        for (File file : javaFiles) {
            if (processFile(file)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static boolean processFile(File file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        try {
            cu = parser.parse(file).getResult().orElse(null);
        } catch (Exception e) {
            System.err.println("Failed to parse " + file + ": " + e.getMessage());
            return false;
        }
        
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // Apply transformations
        TransformationVisitor visitor = new TransformationVisitor();
        cu.accept(visitor, null);
        
        if (visitor.modified) {
            modified = true;
        }
        
        // Add LoaderOptions import if we made changes that require it
        if (visitor.needsLoaderOptionsImport && !hasImport(cu, "org.yaml.snakeyaml.LoaderOptions")) {
            cu.addImport("org.yaml.snakeyaml.LoaderOptions");
            modified = true;
        }
        
        if (modified) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(cu.toString());
            }
            System.out.println("Updated: " + file);
        }
        
        return modified;
    }
    
    private static boolean hasImport(CompilationUnit cu, String importName) {
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals(importName)) {
                return true;
            }
        }
        return false;
    }
    
    private static class TransformationVisitor extends VoidVisitorAdapter<Void> {
        boolean modified = false;
        boolean needsLoaderOptionsImport = false;
        
        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            super.visit(n, arg);
            
            String typeName = n.getType().asString();
            NodeList<Expression> args = n.getArguments();
            
            // Pattern 1: new Constructor(Class)
            if (typeName.contains("Constructor")) {
                if (args.size() == 1) {
                    Expression firstArg = args.get(0);
                    if (firstArg instanceof ClassExpr) {
                        // Transform to new Constructor(Class, new LoaderOptions())
                        ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                        loaderOptions.setType(new ClassOrInterfaceType(null, "LoaderOptions"));
                        
                        n.getArguments().add(loaderOptions);
                        modified = true;
                        needsLoaderOptionsImport = true;
                        System.out.println("  Fixed Constructor(Class) -> Constructor(Class, new LoaderOptions())");
                    }
                }
            }
            
            // Pattern 2: new Representer()
            else if (typeName.contains("Representer") && args.isEmpty()) {
                // Transform to new Representer(new DumperOptions())
                ObjectCreationExpr dumperOptions = new ObjectCreationExpr();
                dumperOptions.setType(new ClassOrInterfaceType(null, "DumperOptions"));
                
                n.getArguments().add(dumperOptions);
                modified = true;
                System.out.println("  Fixed Representer() -> Representer(new DumperOptions())");
            }
            
            // Pattern 3: new Yaml(BaseConstructor, Representer, DumperOptions, Resolver)
            else if (typeName.contains("Yaml")) {
                if (args.size() == 4) {
                    // Simple heuristic: if we have 4 args, insert LoaderOptions as 4th arg
                    ObjectCreationExpr loaderOptions = new ObjectCreationExpr();
                    loaderOptions.setType(new ClassOrInterfaceType(null, "LoaderOptions"));
                    
                    n.getArguments().add(3, loaderOptions);
                    modified = true;
                    needsLoaderOptionsImport = true;
                    System.out.println("  Fixed Yaml(..., Resolver) -> Yaml(..., new LoaderOptions(), Resolver)");
                }
            }
        }
        
        @Override
        public void visit(com.github.javaparser.ast.expr.SuperExpr n, Void arg) {
            super.visit(n, arg);
            
            // Check for super(Model.class) constructor calls
            if (n.getExpression().isPresent()) {
                String expr = n.getExpression().get().toString();
                if (expr.contains("Model.class")) {
                    // This is a super(Model.class) call, need to fix it
                    // But we can't modify SuperExpr directly in this visitor
                    // We need to handle this differently
                    System.out.println("  WARNING: Found super(Model.class) call that needs to be fixed manually");
                    System.out.println("    Change: super(Model.class)");
                    System.out.println("    To: super(Model.class, new LoaderOptions())");
                    needsLoaderOptionsImport = true;
                }
            }
        }
        
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);
            
            // Pattern: protected Set<Property> getProperties(Class<?> type) throws IntrospectionException
            if (n.getNameAsString().equals("getProperties")) {
                // Remove IntrospectionException from throws clause
                boolean removedException = n.getThrownExceptions().removeIf(
                    e -> e.asString().contains("IntrospectionException")
                );
                
                if (removedException) {
                    modified = true;
                    System.out.println("  Removed IntrospectionException from getProperties() throws clause");
                }
            }
        }
    }
}