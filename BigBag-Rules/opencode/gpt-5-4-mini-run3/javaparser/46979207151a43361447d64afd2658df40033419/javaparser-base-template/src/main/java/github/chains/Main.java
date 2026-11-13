package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String STRATEGY_FQN = "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy";
    private static final String STRATEGY_SIMPLE_NAME = "JAXBToStringStrategy";
    private static final String REPLACEMENT_FIELD = "INSTANCE";

    public static void main(String[] args) throws IOException {
        Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        for (Path javaFile : javaFiles) {
            transformFile(javaFile);
        }
    }

    private static void transformFile(Path javaFile) throws IOException {
        CompilationUnit compilationUnit = StaticJavaParser.parse(javaFile);
        AtomicBoolean changed = new AtomicBoolean(false);

        for (MethodCallExpr methodCall : compilationUnit.findAll(MethodCallExpr.class)) {
            if (!isOldStrategyAccess(methodCall)) {
                continue;
            }

            Expression scope = methodCall.getScope().orElseThrow(() ->
                    new IllegalStateException("Expected scoped method call: " + methodCall));
            methodCall.replace(new FieldAccessExpr(scope.clone(), REPLACEMENT_FIELD));
            changed.set(true);
        }

        if (changed.get()) {
            Files.write(javaFile, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean isOldStrategyAccess(MethodCallExpr call) {
        if (!call.getNameAsString().equals("getInstance") || call.getArguments().size() != 0) {
            return false;
        }

        return call.getScope()
                .map(Expression::toString)
                .map(Main::matchesStrategyScope)
                .orElse(false);
    }

    private static boolean matchesStrategyScope(String scopeText) {
        return STRATEGY_SIMPLE_NAME.equals(scopeText)
                || STRATEGY_FQN.equals(scopeText)
                || scopeText.endsWith("." + STRATEGY_SIMPLE_NAME);
    }
}
