package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_METHOD = "getLoggerManager";
    private static final String NEW_RECEIVER_TYPE = "org.codehaus.plexus.MutablePlexusContainer";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(path);
            unit.findAll(MethodCallExpr.class).forEach(Main::rewriteCall);
            Files.write(path, unit.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Best-effort: skip files that cannot be parsed or written.
        }
    }

    private static void rewriteCall(MethodCallExpr expr) {
        if (!OLD_METHOD.equals(expr.getNameAsString()) || expr.getArguments().size() != 0 || !expr.getScope().isPresent()) {
            return;
        }

        Expression scope = expr.getScope().get();
        if (isAlreadyMutablePlexusContainerCast(scope)) {
            return;
        }

        expr.setScope(new EnclosedExpr(new CastExpr(new ClassOrInterfaceType(null, NEW_RECEIVER_TYPE), scope.clone())));
    }

    private static boolean isAlreadyMutablePlexusContainerCast(Expression scope) {
        if (!(scope instanceof CastExpr)) {
            return false;
        }
        CastExpr castExpr = (CastExpr) scope;
        return castExpr.getType().asString().equals(NEW_RECEIVER_TYPE)
                || castExpr.getType().asString().equals("MutablePlexusContainer");
    }
}
