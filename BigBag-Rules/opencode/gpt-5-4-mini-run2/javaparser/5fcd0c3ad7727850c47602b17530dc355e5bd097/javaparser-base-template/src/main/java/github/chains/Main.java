package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one source root path");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        try {
            List<Path> files = Files.walk(sourceRoot)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

            for (Path file : files) {
                transform(file);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(Path file) throws IOException {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(original);

        AtomicBoolean changed = new AtomicBoolean(false);
        cu.accept(new ObsoleteGetClassInfoRemover(changed), null);
        if (changed.get()) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static final class ObsoleteGetClassInfoRemover extends VoidVisitorAdapter<Void> {
        private final AtomicBoolean changed;

        private ObsoleteGetClassInfoRemover(AtomicBoolean changed) {
            this.changed = changed;
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            if (!n.getNameAsString().equals("getClassInfo") || n.getArguments().size() != 1) {
                return;
            }

            if (!looksLikeObsoleteCoverageLookup(n)) {
                return;
            }

            Expression replacement = extractSingletonValue(n.getArgument(0));
            if (replacement == null) {
                return;
            }

            n.replace(replacement);
            changed.set(true);
        }

        private boolean looksLikeObsoleteCoverageLookup(MethodCallExpr call) {
            if (call.getScope().isEmpty()) {
                return false;
            }
            String scope = call.getScope().get().toString();
            return scope.contains("coverage") || scope.contains("CoverageDatabase");
        }

        private Expression extractSingletonValue(Expression expression) {
            if (!(expression instanceof MethodCallExpr)) {
                return null;
            }

            MethodCallExpr call = (MethodCallExpr) expression;
            if (!call.getNameAsString().equals("singleton") || call.getArguments().size() != 1) {
                return null;
            }

            if (call.getScope().isPresent()) {
                Node scope = call.getScope().get();
                if (!(scope instanceof NameExpr) || !((NameExpr) scope).getNameAsString().equals("Collections")) {
                    return null;
                }
            }

            return call.getArgument(0).clone();
        }
    }
}
