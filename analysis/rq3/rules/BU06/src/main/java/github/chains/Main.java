package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String OLD_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
    private static final String NEW_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
    private static final String OLD_CONTEXT_CALL = "setContext";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        try {
            Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            if (rewriteCompilationUnit(cu)) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean rewriteCompilationUnit(CompilationUnit cu) {
        boolean[] changed = new boolean[] {false};

        rewriteImports(cu, changed);

        cu.findAll(Type.class).forEach(type -> {
            if (matchesType(type, OLD_METADATA)) {
                type.replace(StaticJavaParser.parseType(simpleName(NEW_METADATA)));
                changed[0] = true;
            }
        });

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (isOldSetContextCall(call) && call.getScope().isPresent()) {
                call.replace(call.getScope().get().clone());
                changed[0] = true;
            }
        });

        cu.findAll(ExpressionStmt.class).forEach(stmt -> {
            if (stmt.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr call = (MethodCallExpr) stmt.getExpression();
                if (isOldSetContextCall(call) && call.getScope().isEmpty()) {
                    stmt.remove();
                    changed[0] = true;
                }
            }
        });

        return changed[0];
    }

    private static void rewriteImports(CompilationUnit cu, boolean[] changed) {
        List<ImportDeclaration> imports = cu.getImports();
        boolean hasNewImport = false;

        for (int i = imports.size() - 1; i >= 0; i--) {
            ImportDeclaration declaration = imports.get(i);
            String name = declaration.getNameAsString();
            if (OLD_METADATA.equals(name)) {
                imports.remove(i);
                changed[0] = true;
            } else if (NEW_METADATA.equals(name)) {
                hasNewImport = true;
            }
        }

        if (cu.findAll(Type.class).stream().anyMatch(type -> matchesType(type, OLD_METADATA))
            && !hasNewImport) {
            cu.addImport(NEW_METADATA);
            changed[0] = true;
        }
    }

    private static boolean matchesType(Type type, String fullyQualifiedName) {
        String simple = simpleName(fullyQualifiedName);
        return (type.isClassOrInterfaceType() && type.asClassOrInterfaceType().getNameAsString().equals(simple))
            || type.toString().equals(fullyQualifiedName)
            || type.toString().endsWith("." + simple);
    }

    private static boolean isOldSetContextCall(MethodCallExpr call) {
        return call.getNameAsString().equals(OLD_CONTEXT_CALL) && call.getArguments().size() == 1;
    }

    private static String simpleName(String fullyQualifiedName) {
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }
}
