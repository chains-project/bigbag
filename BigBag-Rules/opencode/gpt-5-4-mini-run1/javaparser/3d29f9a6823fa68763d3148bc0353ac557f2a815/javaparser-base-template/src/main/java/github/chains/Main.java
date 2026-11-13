package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_METHOD = "addEnabledLanguages";
    private static final String TARGET_SCOPE_HINT = "AnalysisEngineConfiguration.builder()";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        try {
            try (Stream<Path> paths = Files.walk(sourceRoot)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .sorted(Comparator.naturalOrder())
                        .forEach(Main::rewriteFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite sources under " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean[] changed = new boolean[] {false};

            cu.accept(
                    new ModifierVisitor<Void>() {
                        @Override
                        public Visitable visit(MethodCallExpr call, Void unused) {
                            Visitable visited = super.visit(call, unused);
                            if (visited instanceof MethodCallExpr && isTargetCall((MethodCallExpr) visited)) {
                                MethodCallExpr methodCall = (MethodCallExpr) visited;
                                changed[0] = true;
                                return methodCall.getScope().orElse(methodCall);
                            }
                            return visited;
                        }
                    },
                    null);

            if (changed[0]) {
                Files.writeString(file, cu.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static boolean isTargetCall(MethodCallExpr call) {
        return call.getNameAsString().equals(TARGET_METHOD)
                && call.getScope().map(Expression::toString).orElse("").contains(TARGET_SCOPE_HINT);
    }
}
