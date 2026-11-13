package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFiles::add);
        }
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int filesModified = 0;
        
        for (Path javaFile : javaFiles) {
            boolean modified = false;
            try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
                CompilationUnit cu = parser.parse(in).getResult().orElseThrow();
                
                // Check if file uses DateMidnight
                String fileContent = new String(Files.readAllBytes(javaFile));
                if (!fileContent.contains("DateMidnight")) {
                    continue;
                }
                
                // Transform DateMidnight imports
                modified = transformDateMidnightImports(cu);
                
                // Transform DateMidnight type references
                modified = transformDateMidnightTypeReferences(cu) || modified;
                
                // Transform DateMidnight constructor calls
                modified = transformDateMidnightConstructors(cu, parser) || modified;
                
                // Transform DateMidnight method calls if any
                modified = transformDateMidnightMethodCalls(cu) || modified;
                
                if (modified) {
                    try (FileOutputStream out = new FileOutputStream(javaFile.toFile())) {
                        out.write(cu.toString().getBytes());
                    }
                    filesModified++;
                    System.out.println("Modified: " + javaFile);
                }
            } catch (Exception e) {
                System.err.println("Error processing " + javaFile + ": " + e.getMessage());
            }
        }
        
        System.out.println("Modified " + filesModified + " files");
    }
    
    private static boolean transformDateMidnightImports(CompilationUnit cu) {
        boolean modified = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        // First check if we need to add DateTime import
        boolean hasDateTimeImport = imports.stream()
            .anyMatch(imp -> imp.getNameAsString().equals("org.joda.time.DateTime"));
        
        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration imp = imports.get(i);
            String importName = imp.getNameAsString();
            
            if (importName.equals("org.joda.time.DateMidnight")) {
                // Remove DateMidnight import
                imports.remove(i);
                i--; // Adjust index after removal
                modified = true;
                
                // Add DateTime import if not already present
                if (!hasDateTimeImport) {
                    imports.add(new ImportDeclaration("org.joda.time.DateTime", false, false));
                    hasDateTimeImport = true;
                }
            }
        }
        
        return modified;
    }
    
    private static boolean transformDateMidnightTypeReferences(CompilationUnit cu) {
        DateMidnightTypeVisitor visitor = new DateMidnightTypeVisitor();
        cu.accept(visitor, null);
        return visitor.isModified();
    }
    
    private static boolean transformDateMidnightConstructors(CompilationUnit cu, JavaParser parser) {
        DateMidnightConstructorVisitor visitor = new DateMidnightConstructorVisitor(parser);
        cu.accept(visitor, null);
        return visitor.isModified();
    }
    
    private static boolean transformDateMidnightMethodCalls(CompilationUnit cu) {
        DateMidnightMethodVisitor visitor = new DateMidnightMethodVisitor();
        cu.accept(visitor, null);
        return visitor.isModified();
    }
    
    private static class DateMidnightTypeVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(ClassOrInterfaceType type, Void arg) {
            if (type.getNameAsString().equals("DateMidnight")) {
                modified = true;
                return new ClassOrInterfaceType(null, "DateTime");
            }
            return super.visit(type, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    private static class DateMidnightConstructorVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        private final JavaParser parser;
        
        public DateMidnightConstructorVisitor(JavaParser parser) {
            this.parser = parser;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            if (expr.getType().getNameAsString().equals("DateMidnight")) {
                modified = true;
                
                // For DateMidnight constructors, we should always add .withTimeAtStartOfDay()
                // when converting to DateTime to preserve the semantic meaning
                try {
                    // Build the replacement expression
                    StringBuilder newExpr = new StringBuilder("new org.joda.time.DateTime(");
                    for (int i = 0; i < expr.getArguments().size(); i++) {
                        if (i > 0) newExpr.append(", ");
                        newExpr.append(expr.getArgument(i).toString());
                    }
                    newExpr.append(").withTimeAtStartOfDay()");
                    
                    return parser.parseExpression(newExpr.toString())
                        .getResult().orElse(expr);
                } catch (Exception e) {
                    // Fallback: just replace DateMidnight with DateTime
                    return new ObjectCreationExpr(
                        null,
                        new ClassOrInterfaceType(null, "DateTime"),
                        expr.getArguments()
                    );
                }
            }
            return super.visit(expr, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
    
    private static class DateMidnightMethodVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodCallExpr expr, Void arg) {
            // Method calls on DateMidnight objects should still work on DateTime
            // since DateTime has most of the same methods (inherited from BaseDateTime)
            return super.visit(expr, arg);
        }
        
        public boolean isModified() {
            return modified;
        }
    }
}