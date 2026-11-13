package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class Main {
    
    // Methods that exist in both Matchers and CoreMatchers
    private static final Set<String> COMMON_METHODS = Set.of(
        "containsString", "hasItem", "hasItems", "not", "notNullValue",
        "allOf", "anyOf", "both", "either", "describedAs", "everyItem",
        "is", "isA", "anything", "equalTo", "instanceOf", "sameInstance",
        "nullValue", "theInstance", "startsWith", "endsWith", 
        "containsStringIgnoringCase", "any", "same"
    );
    
    // Methods only in Matchers (not in CoreMatchers)
    private static final Set<String> MATCHERS_ONLY_METHODS = Set.of(
        "emptyIterableOf", "hasProperty", "emptyIterable", "empty",
        "hasSize", "aMapWithSize", "anEmptyMap", "contains",
        "containsInAnyOrder", "containsInRelativeOrder", "hasEntry",
        "hasKey", "hasValue", "comparesEqualTo", "greaterThan",
        "greaterThanOrEqualTo", "lessThan", "lessThanOrEqualTo",
        "iterableWithSize"
    );
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        transformDirectory(sourceDir.toFile());
    }
    
    private static void transformDirectory(File dir) throws Exception {
        if (dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                if (file.isDirectory()) {
                    transformDirectory(file);
                } else if (file.getName().endsWith(".java")) {
                    transformFile(file);
                }
            }
        }
    }
    
    private static void transformFile(File file) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElse(null);
        
        if (cu == null) {
            return;
        }
        
        boolean hasMatchersImport = false;
        boolean hasCoreMatchersImport = false;
        ImportDeclaration matchersImport = null;
        
        // Track which methods are used
        Set<String> usedCommonMethods = new HashSet<>();
        Set<String> usedMatchersOnlyMethods = new HashSet<>();
        
        // Check imports and find Matchers import
        for (ImportDeclaration importDecl : cu.getImports()) {
            if (importDecl.getNameAsString().equals("org.hamcrest.Matchers")) {
                hasMatchersImport = true;
                matchersImport = importDecl;
            }
            if (importDecl.getNameAsString().equals("org.hamcrest.CoreMatchers")) {
                hasCoreMatchersImport = true;
            }
        }
        
        if (!hasMatchersImport) {
            // No Matchers import, nothing to transform
            return;
        }
        
        // Visitor to analyze and transform method calls
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                // Check if this is Matchers.someMethod()
                if (n.getScope().isPresent() && n.getScope().get() instanceof NameExpr) {
                    NameExpr scope = (NameExpr) n.getScope().get();
                    if (scope.getNameAsString().equals("Matchers")) {
                        String methodName = n.getNameAsString();
                        
                        if (COMMON_METHODS.contains(methodName)) {
                            usedCommonMethods.add(methodName);
                            // Change Matchers.method() to CoreMatchers.method()
                            scope.setName("CoreMatchers");
                        } else if (MATCHERS_ONLY_METHODS.contains(methodName)) {
                            usedMatchersOnlyMethods.add(methodName);
                            // Keep as Matchers.method()
                        }
                        // Note: if method is not in either set, we don't change it
                        // (might be a custom method or from different class)
                    }
                }
                
                return super.visit(n, arg);
            }
        }, null);
        
        // Update imports based on what methods are used
        if (!usedCommonMethods.isEmpty() && !hasCoreMatchersImport) {
            cu.addImport("org.hamcrest.CoreMatchers");
        }
        
        // If only common methods are used (no Matchers-only methods),
        // we can remove the Matchers import
        if (usedMatchersOnlyMethods.isEmpty() && matchersImport != null) {
            cu.remove(matchersImport);
        }
        
        // If we made changes, write the file back
        if (!usedCommonMethods.isEmpty() || !usedMatchersOnlyMethods.isEmpty()) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(cu.toString());
            }
            System.out.println("Transformed: " + file.getPath());
            if (!usedCommonMethods.isEmpty()) {
                System.out.println("  Changed to CoreMatchers: " + usedCommonMethods);
            }
            if (!usedMatchersOnlyMethods.isEmpty()) {
                System.out.println("  Kept as Matchers: " + usedMatchersOnlyMethods);
            }
        }
    }
}