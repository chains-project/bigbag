package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
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
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String TARGET_TYPE = "org.kohsuke.github.GHCompare";
    private static final String OLD_FIELD = "status";
    private static final String NEW_METHOD = "getStatus";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(sourceRoot));
        findGithubApiJars().forEach(jar -> addJarSolver(typeSolver, jar));

        ParserConfiguration configuration = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(configuration);

        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            javaFiles = walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }

        for (Path javaFile : javaFiles) {
            rewrite(javaFile, typeSolver);
        }
    }

    private static void rewrite(Path javaFile, CombinedTypeSolver typeSolver) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        LexicalPreservingPrinter.setup(cu);

        boolean[] changed = new boolean[] {false};
        cu.findAll(FieldAccessExpr.class).forEach(fieldAccess -> {
            if (shouldReplace(fieldAccess, typeSolver)) {
                Expression scope = fieldAccess.getScope().clone();
                fieldAccess.replace(new MethodCallExpr(scope, NEW_METHOD));
                changed[0] = true;
            }
        });

        if (changed[0]) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean shouldReplace(FieldAccessExpr fieldAccess, CombinedTypeSolver typeSolver) {
        if (!OLD_FIELD.equals(fieldAccess.getNameAsString())) {
            return false;
        }

        try {
            ResolvedType scopeType = JavaParserFacade.get(typeSolver).getType(fieldAccess.getScope());
            if (scopeType.isReferenceType()) {
                ResolvedReferenceType referenceType = scopeType.asReferenceType();
                return TARGET_TYPE.equals(referenceType.getQualifiedName());
            }
        } catch (RuntimeException ignored) {
            return false;
        }

        return false;
    }

    private static List<Path> findGithubApiJars() {
        Path repoRoot = Paths.get(System.getProperty("user.home"), ".m2", "repository", "org", "kohsuke", "github-api");
        if (!Files.isDirectory(repoRoot)) {
            return List.of();
        }

        List<Path> jars = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoRoot)) {
            walk.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".jar")
                            && !path.getFileName().toString().contains("sources")
                            && !path.getFileName().toString().contains("javadoc"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(jars::add);
        } catch (IOException ignored) {
            return List.of();
        }
        return jars;
    }

    private static void addJarSolver(CombinedTypeSolver typeSolver, Path jar) {
        try {
            typeSolver.add(new JarTypeSolver(jar));
        } catch (IOException ignored) {
            // If a jar cannot be added, keep going with the rest of the source set.
        }
    }
}
