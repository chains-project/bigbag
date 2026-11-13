package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {

    private static final String TARGET_TYPE = "org.kohsuke.github.GHCompare";
    private static final String TARGET_FIELD = "status";
    private static final String TARGET_ACCESSOR = "getStatus";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }
        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        configureParser(sourceRoot);
        try {
            transform(sourceRoot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void configureParser(Path sourceRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));

        Path githubApiJar = Paths.get(System.getProperty("user.home"), ".m2", "repository", "org", "kohsuke", "github-api", "1.314", "github-api-1.314.jar");
        if (Files.exists(githubApiJar)) {
            try {
                typeSolver.add(new JarTypeSolver(githubApiJar));
            } catch (IOException e) {
                throw new RuntimeException("Unable to load type solver for " + githubApiJar, e);
            }
        }

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(configuration);
    }

    private static void transform(Path sourceRoot) throws IOException {
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            FieldToAccessorVisitor visitor = new FieldToAccessorVisitor();
            visitor.visit(cu, null);
            if (visitor.changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static final class FieldToAccessorVisitor extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public Visitable visit(FieldAccessExpr n, Void arg) {
            if (shouldRewrite(n)) {
                changed = true;
                Expression scope = n.getScope().clone();
                return new MethodCallExpr(scope, TARGET_ACCESSOR);
            }
            return super.visit(n, arg);
        }

        private boolean shouldRewrite(FieldAccessExpr n) {
            if (!TARGET_FIELD.equals(n.getNameAsString())) {
                return false;
            }
            if (matchesTargetType(n)) {
                return true;
            }
            return hasGitHubCompareImport(n);
        }

        private boolean matchesTargetType(FieldAccessExpr n) {
            try {
                return n.getScope().calculateResolvedType().describe().equals(TARGET_TYPE);
            } catch (RuntimeException ex) {
                return false;
            }
        }

        private boolean hasGitHubCompareImport(Node node) {
            Optional<CompilationUnit> cu = node.findCompilationUnit();
            if (cu.isEmpty()) {
                return false;
            }
            return cu.get().getImports().stream().anyMatch(importDecl -> {
                String name = importDecl.getNameAsString();
                return TARGET_TYPE.equals(name) || importDecl.isAsterisk() && "org.kohsuke.github".equals(name);
            });
        }
    }
}
