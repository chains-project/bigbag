package github.chains;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String TARGET_TYPE = "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy";
    private static final String TARGET_SIMPLE_NAME = "JAXBToStringStrategy";
    private static final String TARGET_METHOD = "getInstance";
    private static final String TARGET_FIELD = "INSTANCE";
    private static final Set<String> TARGET_SIMPLE_NAMES = new HashSet<>(Arrays.asList(
            "DefaultToStringStrategy",
            "JAXBToStringStrategy",
            "DefaultEqualsStrategy",
            "JAXBEqualsStrategy",
            "DefaultHashCodeStrategy",
            "JAXBHashCodeStrategy",
            "DefaultCopyStrategy",
            "JAXBCopyStrategy"
    ));

    public static void main(String[] args) {
        Path inputRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        try {
            List<Path> javaFiles = collectJavaFiles(inputRoot);
            CombinedTypeSolver typeSolver = buildTypeSolver(javaFiles);
            StaticJavaParser.setConfiguration(new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver)));

            AtomicInteger replacements = new AtomicInteger();
            for (Path javaFile : javaFiles) {
                transformFile(javaFile, replacements);
            }

            System.out.println("Updated " + replacements.get() + " call(s) under " + inputRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to process Java sources", e);
        }
    }

    private static List<Path> collectJavaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static CombinedTypeSolver buildTypeSolver(List<Path> javaFiles) {
        Set<Path> sourceRoots = new HashSet<>();
        for (Path javaFile : javaFiles) {
            Path sourceRoot = detectSourceRoot(javaFile);
            if (sourceRoot != null) {
                sourceRoots.add(sourceRoot);
            }
        }

        CombinedTypeSolver solver = new CombinedTypeSolver();
        solver.add(new ReflectionTypeSolver());
        for (Path sourceRoot : sourceRoots) {
            solver.add(new JavaParserTypeSolver(sourceRoot));
        }
        return solver;
    }

    private static Path detectSourceRoot(Path javaFile) {
        Path current = javaFile.getParent();
        while (current != null) {
            String dirName = current.getFileName() == null ? "" : current.getFileName().toString();
            if ("java".equals(dirName)) {
                Path parent = current.getParent();
                if (parent != null) {
                    String parentName = parent.getFileName() == null ? "" : parent.getFileName().toString();
                    if ("main".equals(parentName) || "test".equals(parentName)) {
                        return current;
                    }
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private static void transformFile(Path javaFile, AtomicInteger replacements) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaFile);
            LexicalPreservingPrinter.setup(cu);
            List<MethodCallExpr> matches = new ArrayList<>();

            cu.findAll(MethodCallExpr.class).forEach(call -> {
                if (isTargetCall(call)) {
                    matches.add(call);
                }
            });

            for (MethodCallExpr call : matches) {
                Expression scope = call.getScope().orElse(null);
                if (scope != null) {
                    call.replace(new FieldAccessExpr(scope.clone(), TARGET_FIELD));
                    replacements.incrementAndGet();
                }
            }

            if (!matches.isEmpty()) {
                Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (ParseProblemException e) {
            throw new RuntimeException("Failed to parse " + javaFile, e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write " + javaFile, e);
        }
    }

    private static boolean isTargetCall(MethodCallExpr expr) {
        if (!TARGET_METHOD.equals(expr.getNameAsString()) || !expr.getArguments().isEmpty() || expr.getScope().isEmpty()) {
            return false;
        }

        Expression scope = expr.getScope().orElse(null);
        if (scope == null) {
            return false;
        }

        return matchesByText(scope);
    }

    private static boolean matchesByText(Expression scope) {
        String text = scope.toString();
        if (TARGET_TYPE.equals(text)) {
            return true;
        }
        if (TARGET_SIMPLE_NAMES.contains(text)) {
            return true;
        }
        for (String simpleName : TARGET_SIMPLE_NAMES) {
            if (text.endsWith('.' + simpleName)) {
                return true;
            }
        }
        return false;
    }

}
