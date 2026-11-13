package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BinaryExpr.Operator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    private static final Map<String, String> REPLACEMENTS = new LinkedHashMap<>();

    static {
        REPLACEMENTS.put("io.dropwizard.Application", "io.dropwizard.core.Application");
        REPLACEMENTS.put("io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
        REPLACEMENTS.put("io.dropwizard.setup.", "io.dropwizard.core.setup.");
        REPLACEMENTS.put("io.dropwizard.cli.", "io.dropwizard.core.cli.");
        REPLACEMENTS.put("io.dropwizard.server.", "io.dropwizard.core.server.");
        REPLACEMENTS.put("jakarta.annotation.Nullable", "javax.annotation.Nullable");
        REPLACEMENTS.put("com.fasterxml.jackson.jaxrs.json.", "com.fasterxml.jackson.jakarta.rs.json.");

        REPLACEMENTS.put("javax.validation.", "jakarta.validation.");
        REPLACEMENTS.put("javax.servlet.", "jakarta.servlet.");
        REPLACEMENTS.put("javax.ws.rs.", "jakarta.ws.rs.");
        REPLACEMENTS.put("javax.ws.rs", "jakarta.ws.rs");
        REPLACEMENTS.put("javax.validation", "jakarta.validation");
        REPLACEMENTS.put("javax.servlet", "jakarta.servlet");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: the source root to rewrite");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            boolean changed = false;

            for (ImportDeclaration importDeclaration : cu.getImports()) {
                if (importDeclaration.getNameAsString().equals("io.dropwizard.util.Strings")) {
                    importDeclaration.remove();
                    changed = true;
                    continue;
                }
                String rewritten = rewriteQualifiedName(importDeclaration.getNameAsString());
                if (!rewritten.equals(importDeclaration.getNameAsString())) {
                    importDeclaration.setName(rewritten);
                    changed = true;
                }
            }

            List<ClassOrInterfaceType> types = cu.findAll(ClassOrInterfaceType.class);
            for (ClassOrInterfaceType type : types) {
                String rewritten = rewriteQualifiedName(type.asString());
                if (!rewritten.equals(type.asString())) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(rewritten));
                    changed = true;
                }
            }

            List<UnaryExpr> unaryExprs = cu.findAll(UnaryExpr.class);
            for (UnaryExpr unaryExpr : unaryExprs) {
                if (unaryExpr.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT
                        && unaryExpr.getExpression().isMethodCallExpr()) {
                    MethodCallExpr call = unaryExpr.getExpression().asMethodCallExpr();
                    if (call.getScope().isPresent()
                            && call.getScope().get().isNameExpr()
                            && call.getScope().get().asNameExpr().getNameAsString().equals("Strings")
                            && call.getNameAsString().equals("isNullOrEmpty")
                            && call.getArguments().size() == 1) {
                        Expression value = call.getArgument(0);
                        unaryExpr.replace(new BinaryExpr(
                                new BinaryExpr(value.clone(), new NullLiteralExpr(), Operator.NOT_EQUALS),
                                new MethodCallExpr(value.clone(), "isEmpty"),
                                Operator.AND));
                        changed = true;
                    }
                }
            }

            if (changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Skip files that fail to parse or rewrite cleanly.
        }
    }

    private static String rewriteQualifiedName(String name) {
        String rewritten = name;
        for (Map.Entry<String, String> entry : REPLACEMENTS.entrySet()) {
            if (rewritten.equals(entry.getKey()) || rewritten.startsWith(entry.getKey() + ".") || rewritten.startsWith(entry.getKey())) {
                rewritten = entry.getValue() + rewritten.substring(entry.getKey().length());
                break;
            }
        }
        return rewritten;
    }
}
