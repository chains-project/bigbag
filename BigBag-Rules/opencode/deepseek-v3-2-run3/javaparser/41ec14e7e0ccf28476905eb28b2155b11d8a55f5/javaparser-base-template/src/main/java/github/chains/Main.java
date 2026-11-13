package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
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
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
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
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static boolean processFile(Path file) throws IOException {
        String content = Files.readString(file);
        CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
        
        boolean modified = false;
        
        // Fix javax.validation imports to jakarta.validation
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("javax.validation")) {
                String newImport = importName.replace("javax.validation", "jakarta.validation");
                importDecl.setName(newImport);
                modified = true;
                System.out.println("Changed import in " + file + ": " + importName + " -> " + newImport);
            }
        }
        
        // Remove commons-beanutils imports
        List<ImportDeclaration> importsToRemove = new ArrayList<>();
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            if (importName.startsWith("org.apache.commons.beanutils")) {
                importsToRemove.add(importDecl);
                modified = true;
                System.out.println("Removing import in " + file + ": " + importName);
            }
        }
        cu.getImports().removeAll(importsToRemove);
        
        // Replace PropertyUtils.getPropertyDescriptors with Introspector.getBeanInfo
        PropertyUtilsReplacer replacer = new PropertyUtilsReplacer(cu);
        cu.accept(replacer, null);
        if (replacer.isModified()) {
            modified = true;
        }
        
        if (modified) {
            Files.writeString(file, cu.toString());
        }
        
        return modified;
    }
    
    private static class PropertyUtilsReplacer extends ModifierVisitor<Void> {
        private boolean modified = false;
        private CompilationUnit cu;
        
        public PropertyUtilsReplacer(CompilationUnit cu) {
            this.cu = cu;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            Expression scope = n.getScope().orElse(null);
            if (scope instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) scope;
                if (nameExpr.getNameAsString().equals("PropertyUtils")) {
                    String methodName = n.getNameAsString();
                    NodeList<Expression> args = n.getArguments();
                    
                    if (methodName.equals("getPropertyDescriptors") && args.size() == 1) {
                        // PropertyUtils.getPropertyDescriptors(class)
                        Expression classExpr = args.get(0);
                        
                        // Add import for java.beans.Introspector if needed
                        boolean hasIntrospectorImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("java.beans.Introspector"));
                        if (!hasIntrospectorImport) {
                            cu.addImport("java.beans.Introspector");
                        }
                        
                        // Replace: PropertyUtils.getPropertyDescriptors(MyClass.class)
                        // With: Introspector.getBeanInfo(MyClass.class).getPropertyDescriptors()
                        MethodCallExpr getBeanInfo = new MethodCallExpr(new NameExpr("Introspector"), new SimpleName("getBeanInfo"));
                        getBeanInfo.addArgument(classExpr);
                        MethodCallExpr newCall = new MethodCallExpr(getBeanInfo, new SimpleName("getPropertyDescriptors"));
                        n.replace(newCall);
                        modified = true;
                    } else if (methodName.equals("getPropertyDescriptor") && args.size() == 2) {
                        // PropertyUtils.getPropertyDescriptor(object, propertyName)
                        // This is more complex - needs helper method
                        // For now, just warn and add imports
                        
                        // Add import for java.beans.Introspector if needed
                        boolean hasIntrospectorImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("java.beans.Introspector"));
                        if (!hasIntrospectorImport) {
                            cu.addImport("java.beans.Introspector");
                        }
                        
                        boolean hasPropertyDescriptorImport = cu.getImports().stream()
                            .anyMatch(imp -> imp.getNameAsString().equals("java.beans.PropertyDescriptor"));
                        if (!hasPropertyDescriptorImport) {
                            cu.addImport("java.beans.PropertyDescriptor");
                        }
                        
                        System.out.println("WARNING: PropertyUtils.getPropertyDescriptor needs manual fix");
                        modified = true;
                    }
                }
            }
            return super.visit(n, arg);
        }
        
        public boolean isModified() { return modified; }
    }
}