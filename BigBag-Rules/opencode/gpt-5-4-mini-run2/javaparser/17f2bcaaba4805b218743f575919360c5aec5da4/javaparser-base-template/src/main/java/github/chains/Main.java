package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

public class Main {

    private static final String JUNIT4_ASSERT = "org.junit.Assert";
    private static final String JUNIT4_ASSUME = "org.junit.Assume";
    private static final String JUNIT4_IGNORE = "org.junit.Ignore";

    private static final String JUNIT5_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    private static final String JUNIT5_ASSUMPTIONS = "org.junit.jupiter.api.Assumptions";
    private static final String JUNIT5_DISABLED = "org.junit.jupiter.api.Disabled";

    private static final List<String> ASSERTION_METHODS = Arrays.asList(
            "assertArrayEquals", "assertEquals", "assertFalse", "assertIterableEquals", "assertLinesMatch",
            "assertNotEquals", "assertNotNull", "assertNotSame", "assertNull", "assertSame", "assertThrows",
            "assertTimeout", "assertTimeoutPreemptively", "assertTrue", "fail");

    private static final List<String> ASSUMPTION_METHODS = Arrays.asList("assumeFalse", "assumeTrue");

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source directory to transform");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Input path is not a directory: " + root);
        }

        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            String original = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            CompilationUnit cu = StaticJavaParser.parse(original);
            if (transformCompilationUnit(cu)) {
                Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = false;

        NodeList<ImportDeclaration> imports = cu.getImports();
        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration imp = imports.get(i);
            String name = imp.getNameAsString();
            String replacement = null;

            if (imp.isStatic()) {
                if (name.equals(JUNIT4_ASSERT) || name.startsWith(JUNIT4_ASSERT + ".")) {
                    replacement = JUNIT5_ASSERTIONS + name.substring(JUNIT4_ASSERT.length());
                } else if (name.equals(JUNIT4_ASSUME) || name.startsWith(JUNIT4_ASSUME + ".")) {
                    replacement = JUNIT5_ASSUMPTIONS + name.substring(JUNIT4_ASSUME.length());
                }
            } else {
                if (name.equals(JUNIT4_ASSERT)) {
                    replacement = JUNIT5_ASSERTIONS;
                } else if (name.equals(JUNIT4_ASSUME)) {
                    replacement = JUNIT5_ASSUMPTIONS;
                } else if (name.equals(JUNIT4_IGNORE)) {
                    replacement = JUNIT5_DISABLED;
                }
            }

            if (replacement != null) {
                imports.set(i, new ImportDeclaration(replacement, imp.isStatic(), imp.isAsterisk()));
                changed = true;
            }
        }

        changed |= cu.findAll(AnnotationExpr.class).stream().anyMatch(Main::rewriteAnnotation);
        changed |= cu.findAll(MethodCallExpr.class).stream().anyMatch(Main::rewriteMethodCall);
        changed |= cu.findAll(NameExpr.class).stream().anyMatch(Main::rewriteNameExpr);

        return changed;
    }

    private static boolean rewriteAnnotation(AnnotationExpr annotation) {
        if (annotation.getNameAsString().equals("Ignore")) {
            annotation.setName("Disabled");
            return true;
        }
        return false;
    }

    private static boolean rewriteMethodCall(MethodCallExpr call) {
        if (!call.getScope().isPresent()) {
            return false;
        }

        Node scope = call.getScope().get();
        if (!(scope instanceof NameExpr)) {
            return false;
        }

        NameExpr nameExpr = (NameExpr) scope;
        String scopeName = nameExpr.getNameAsString();
        String methodName = call.getNameAsString();

        if (scopeName.equals("Assert") && ASSERTION_METHODS.contains(methodName)) {
            nameExpr.setName("Assertions");
            return true;
        }
        if (scopeName.equals("Assume") && ASSUMPTION_METHODS.contains(methodName)) {
            nameExpr.setName("Assumptions");
            return true;
        }
        return false;
    }

    private static boolean rewriteNameExpr(NameExpr nameExpr) {
        String name = nameExpr.getNameAsString();
        if (name.equals("Assert")) {
            nameExpr.setName("Assertions");
            return true;
        }
        if (name.equals("Assume")) {
            nameExpr.setName("Assumptions");
            return true;
        }
        return false;
    }
}
