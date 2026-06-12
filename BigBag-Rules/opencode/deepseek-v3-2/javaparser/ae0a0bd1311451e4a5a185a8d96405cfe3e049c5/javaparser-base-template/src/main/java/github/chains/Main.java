package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.types.ResolvedType;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    // Configuration: Old API patterns to transform
    private static final List<ApiChange> API_CHANGES = List.of(new ApiChange("org.hamcrest.core.StringContains", "containsString", "containsStringIgnoringCase"), new ApiChange("org.hamcrest.core.StringStartsWith", "startsWith", "startsWithIgnoringCase"));

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Transforming files in: " + sourceDir.toAbsolutePath());
        try (Stream<Path> paths = Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(Main::transformFile);
        }
        System.out.println("Transformation complete!");
    }

    private static void transformFile(Path file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu == null) {
                System.err.println("Failed to parse: " + file);
                return;
            }
            HamcrestTransformer transformer = new HamcrestTransformer(cu);
            cu.accept(transformer, null);
            Files.write(file, cu.toString().getBytes());
            System.out.println("Transformed: " + file);
        } catch (IOException e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }

    private static class HamcrestTransformer extends ModifierVisitor<Void> {

        private final CompilationUnit cu;
        private boolean needsMatchersImport = false;

        public HamcrestTransformer(CompilationUnit cu) {
            this.cu = cu;
        }

        @Override
        public Visitable visit(ObjectCreationExpr expr, Void arg) {
            // Try to resolve the type to check if it matches our API changes
            for (ApiChange apiChange : API_CHANGES) {
                if (isMatchingType(expr, apiChange.oldType)) {
                    // Check if it has 2 arguments (boolean, String)
                    if (expr.getArguments().size() == 2) {
                        Expression firstArg = expr.getArgument(0);
                        Expression secondArg = expr.getArgument(1);
                        // Check if first argument is a boolean literal
                        if (firstArg instanceof BooleanLiteralExpr) {
                            boolean ignoreCase = ((BooleanLiteralExpr) firstArg).getValue();
                            String methodName = ignoreCase ? apiChange.ignoringCaseMethod : apiChange.caseSensitiveMethod;
                            // Create static method call
                            MethodCallExpr methodCall = new MethodCallExpr();
                            // Use Matchers instead of StringContains/StringStartsWith
                            methodCall.setScope(new NameExpr("Matchers"));
                            methodCall.setName(methodName);
                            methodCall.addArgument(secondArg);
                            
                            // Mark that we need to import Matchers
                            needsMatchersImport = true;
                            
                            return methodCall;
                        }
                    }
                }
            }
            return super.visit(expr, arg);
        }
        
        @Override
        public Visitable visit(CompilationUnit cu, Void arg) {
            // Reset flag for each file
            needsMatchersImport = false;
            Visitable result = super.visit(cu, arg);
            
            // Add Matchers import if needed and not already present
            if (needsMatchersImport && !hasMatchersImport(cu)) {
                cu.addImport("org.hamcrest.Matchers");
            }
            
            return result;
        }
        
        private boolean hasMatchersImport(CompilationUnit cu) {
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().equals("org.hamcrest.Matchers")) {
                    return true;
                }
            }
            return false;
        }

        private boolean isMatchingType(ObjectCreationExpr expr, String fullTypeName) {
            String typeName = expr.getType().asString();
            String simpleName = getSimpleName(fullTypeName);
            // Check if type name matches (could be simple or fully qualified)
            if (typeName.equals(simpleName) || typeName.equals(fullTypeName)) {
                return true;
            }
            // Check imports to see if simple name refers to our type
            if (typeName.equals(simpleName)) {
                return isTypeImported(cu, fullTypeName);
            }
            return false;
        }

        private boolean isTypeImported(CompilationUnit cu, String fullTypeName) {
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().equals(fullTypeName) || (importDecl.isAsterisk() && fullTypeName.startsWith(importDecl.getNameAsString() + "."))) {
                    return true;
                }
            }
            return false;
        }

        private String getSimpleName(String fullTypeName) {
            int lastDot = fullTypeName.lastIndexOf('.');
            return lastDot >= 0 ? fullTypeName.substring(lastDot + 1) : fullTypeName;
        }
    }

    private static class ApiChange {

        final String oldType;

        final String caseSensitiveMethod;

        final String ignoringCaseMethod;

        ApiChange(String oldType, String caseSensitiveMethod, String ignoringCaseMethod) {
            this.oldType = oldType;
            this.caseSensitiveMethod = caseSensitiveMethod;
            this.ignoringCaseMethod = ignoringCaseMethod;
        }
    }
}
