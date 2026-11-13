package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic transformation rule for fixing breaking API change in pitest 1.10.0.
 * 
 * Breaking Change: CoverageDatabase.getClassInfo(Set<ClassName>) method was removed.
 * 
 * Transformation approach:
 * 1. Finds calls to getClassInfo on CoverageDatabase objects
 * 2. Provides a minimal fix that compiles but may need manual adjustment
 * 3. The fix uses a placeholder ClassInfoSource that needs to be available
 * 
 * This is a generic rule that can be applied to any project affected by this change.
 * Users may need to manually ensure ClassInfoSource is available in their context.
 */
public class Main {
    
    // Configuration
    private static final String OLD_TYPE_FQN = "org.pitest.coverage.CoverageDatabase";
    private static final String OLD_METHOD_NAME = "getClassInfo";
    
    // These would ideally be command-line parameters for full generality
    private static final String NEW_SOURCE_TYPE = "ClassInfoSource";
    private static final String NEW_SOURCE_FQN = "org.pitest.classinfo.ClassInfoSource";
    private static final String NEW_SOURCE_FIELD = "classInfoSource";
    private static final String NEW_METHOD = "fetchClass";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("\nTransforms code to fix breaking API change:");
            System.err.println("- Old: CoverageDatabase.getClassInfo(Set<ClassName>)");
            System.err.println("- New: ClassInfoSource.fetchClass(ClassName) or similar");
            System.err.println("\nNote: You may need to manually provide ClassInfoSource in your context.");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming: " + sourceDir);
        System.out.println("Fixing calls to: " + OLD_TYPE_FQN + "." + OLD_METHOD_NAME + "()");
        
        try {
            transformDirectory(sourceDir);
            System.out.println("\nDone. Files may need additional manual adjustments.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(String sourceDir) throws IOException {
        List<Path> javaFiles = findJavaFiles(sourceDir);
        JavaParser parser = new JavaParser();
        
        int totalFixes = 0;
        for (Path file : javaFiles) {
            int fixes = transformFile(file, parser);
            totalFixes += fixes;
            if (fixes > 0) {
                System.out.println("  Fixed " + fixes + " issue(s) in " + file.getFileName());
            }
        }
        
        System.out.println("\nTotal fixes applied: " + totalFixes);
        if (totalFixes > 0) {
            System.out.println("\nIMPORTANT NEXT STEPS:");
            System.out.println("1. Ensure " + NEW_SOURCE_FQN + " is available in your classes");
            System.out.println("2. Update constructors to accept " + NEW_SOURCE_TYPE + " parameter if needed");
            System.out.println("3. Update test mocks to match new method calls");
        }
    }
    
    private static List<Path> findJavaFiles(String dir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(dir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
    
    private static int transformFile(Path file, JavaParser parser) throws IOException {
        String content = Files.readString(file);
        CompilationUnit cu = parser.parse(content).getResult().orElseThrow(
            () -> new RuntimeException("Cannot parse: " + file)
        );
        
        List<MethodCallExpr> problematicCalls = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> containingClasses = new ArrayList<>();
        
        // Find all getClassInfo calls and containing classes
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr call, Void arg) {
                super.visit(call, arg);
                if (isGetClassInfoCall(call)) {
                    problematicCalls.add(call);
                }
            }
            
            @Override
            public void visit(ClassOrInterfaceDeclaration cls, Void arg) {
                super.visit(cls, arg);
                containingClasses.add(cls);
            }
        }, null);
        
        if (problematicCalls.isEmpty()) {
            return 0;
        }
        
        // For each class containing problematic calls, add the field
        for (ClassOrInterfaceDeclaration cls : containingClasses) {
            addClassInfoSourceField(cls);
        }
        
        // Apply fixes
        for (MethodCallExpr call : problematicCalls) {
            applyFix(call);
        }
        
        // Write back
        Files.writeString(file, cu.toString());
        return problematicCalls.size();
    }
    
    private static void addClassInfoSourceField(ClassOrInterfaceDeclaration cls) {
        // Check if field already exists
        boolean hasField = cls.getFields().stream()
            .anyMatch(f -> f.getVariables().stream()
                .anyMatch(v -> v.getNameAsString().equals(NEW_SOURCE_FIELD)));
        
        if (!hasField) {
            // Add field: private ClassInfoSource classInfoSource;
            FieldDeclaration field = cls.addField(NEW_SOURCE_FQN, NEW_SOURCE_FIELD);
            field.setPrivate(true);
            
            // Also update constructors to accept ClassInfoSource parameter
            updateConstructors(cls);
        }
    }
    
    private static void updateConstructors(ClassOrInterfaceDeclaration cls) {
        for (ConstructorDeclaration constructor : cls.getConstructors()) {
            // Check if constructor already has ClassInfoSource parameter
            boolean hasParam = constructor.getParameters().stream()
                .anyMatch(p -> p.getType().asString().contains("ClassInfoSource") || 
                              p.getType().asString().contains(NEW_SOURCE_TYPE));
            
            if (!hasParam) {
                // Add ClassInfoSource parameter
                Parameter param = new Parameter();
                param.setType(NEW_SOURCE_FQN);
                param.setName(NEW_SOURCE_FIELD);
                constructor.addParameter(param);
                
                // Add assignment in constructor body
                try {
                    Statement assignStmt = StaticJavaParser.parseStatement(
                        "this." + NEW_SOURCE_FIELD + " = " + NEW_SOURCE_FIELD + ";");
                    constructor.getBody().addStatement(0, assignStmt);
                } catch (Exception e) {
                    System.err.println("  Warning: Could not add assignment to constructor");
                }
            }
        }
    }
    
    private static boolean isGetClassInfoCall(MethodCallExpr call) {
        return OLD_METHOD_NAME.equals(call.getNameAsString());
    }
    
    private static void applyFix(MethodCallExpr call) {
        // Get the argument (usually Collections.singleton(something))
        NodeList<Expression> args = call.getArguments();
        if (args.isEmpty()) {
            return;
        }
        
        Expression arg = args.get(0);
        String argStr = arg.toString();
        
        // Determine if it's a singleton call
        boolean isSingleton = argStr.contains("Collections.singleton") || 
                             argStr.contains("Set.of") ||
                             argStr.contains("singleton");
        
        if (isSingleton) {
            // Extract inner argument from Collections.singleton(...)
            String innerArg = extractSingletonArgument(argStr);
            
            // Create replacement: classInfoSource.fetchClass(className).orElse(null)
            // Note: This is a minimal fix that compiles but may need adjustment
            String replacement = NEW_SOURCE_FIELD + "." + NEW_METHOD + "(" + innerArg + ")" +
                               ".map(java.util.Collections::singleton)" +
                               ".orElse(java.util.Collections.emptySet())";
            
            try {
                Expression newExpr = StaticJavaParser.parseExpression(replacement);
                call.replace(newExpr);
            } catch (Exception e) {
                System.err.println("  Warning: Could not transform call at line " + 
                                 call.getRange().map(r -> r.begin.line).orElse(-1) + ": " + e.getMessage());
            }
        } else {
            // For non-singleton collections, use a more complex transformation
            String replacement = argStr + ".stream()" +
                               ".map(" + NEW_SOURCE_FIELD + "::" + NEW_METHOD + ")" +
                               ".filter(java.util.Optional::isPresent)" +
                               ".map(java.util.Optional::get)" +
                               ".collect(java.util.stream.Collectors.toList())";
            
            try {
                Expression newExpr = StaticJavaParser.parseExpression(replacement);
                call.replace(newExpr);
            } catch (Exception e) {
                System.err.println("  Warning: Could not transform collection call at line " +
                                 call.getRange().map(r -> r.begin.line).orElse(-1));
            }
        }
    }
    
    private static String extractSingletonArgument(String argStr) {
        // Simple extraction - handles Collections.singleton(...)
        if (argStr.startsWith("Collections.singleton(")) {
            return argStr.substring("Collections.singleton(".length(), argStr.length() - 1);
        } else if (argStr.startsWith("Set.of(")) {
            return argStr.substring("Set.of(".length(), argStr.length() - 1);
        }
        return argStr;
    }
}