package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_METHOD = "getCompare";
    private static final String TARGET_FIELD = "status";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        root = root.toAbsolutePath().normalize();

        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        for (Path sourceRoot : discoverSourceRoots(root)) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        }

        ParserConfiguration configuration = new ParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(configuration);

        int changes = 0;
        for (Path javaFile : discoverJavaFiles(root)) {
            changes += rewriteFile(javaFile, typeSolver);
        }

        System.out.println("Applied " + changes + " transformation(s).");
    }

    private static int rewriteFile(Path javaFile, CombinedTypeSolver typeSolver) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        LexicalPreservingPrinter.setup(cu);

        ChangeVisitor visitor = new ChangeVisitor();
        visitor.visit(cu, null);
        if (!visitor.modified) {
            return 0;
        }

        Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        return visitor.replacements;
    }

    private static List<Path> discoverJavaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/target/"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private static List<Path> discoverSourceRoots(Path root) throws IOException {
        List<Path> roots = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isDirectory)
                .filter(Main::isJavaSourceRoot)
                .forEach(roots::add);
        }
        roots.sort(Comparator.naturalOrder());
        return roots;
    }

    private static boolean isJavaSourceRoot(Path dir) {
        Path fileName = dir.getFileName();
        if (fileName == null || !"java".equals(fileName.toString())) {
            return false;
        }
        Path parent = dir.getParent();
        Path grandParent = parent != null ? parent.getParent() : null;
        return parent != null && grandParent != null && "src".equals(grandParent.getFileName().toString());
    }

    private static final class ChangeVisitor extends ModifierVisitor<Void> {
        private boolean modified;
        private int replacements;

        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            if (isTargetFieldAccess(n)) {
                modified = true;
                replacements++;
                return new MethodCallExpr(n.getScope().clone(), "getStatus");
            }
            return super.visit(n, arg);
        }

        private boolean isTargetFieldAccess(FieldAccessExpr n) {
            if (!TARGET_FIELD.equals(n.getNameAsString())) {
                return false;
            }
            return containsTargetMethod(n.getScope());
        }

        private boolean containsTargetMethod(Expression expression) {
            if (expression == null) {
                return false;
            }
            if (expression.isMethodCallExpr()) {
                MethodCallExpr methodCall = expression.asMethodCallExpr();
                if (TARGET_METHOD.equals(methodCall.getNameAsString())) {
                    return true;
                }
                return containsTargetMethod(methodCall.getScope().orElse(null));
            }
            if (expression.isFieldAccessExpr()) {
                return containsTargetMethod(expression.asFieldAccessExpr().getScope());
            }
            if (expression.isEnclosedExpr()) {
                return containsTargetMethod(expression.asEnclosedExpr().getInner());
            }
            return false;
        }
    }

}
