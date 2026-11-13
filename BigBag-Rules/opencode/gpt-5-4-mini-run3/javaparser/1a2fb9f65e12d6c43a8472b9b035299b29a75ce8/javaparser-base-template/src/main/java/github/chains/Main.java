package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;

import com.github.javaparser.ParseResult;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.utils.SourceRoot;

public class Main {
    private static final String OLD_PACKAGE = "javax.validation";
    private static final String NEW_PACKAGE = "jakarta.validation";

    public static void main(String[] args) throws Exception {
        Path inputPath = Paths.get(args.length > 0 ? args[0] : ".");
        for (Path sourceRootPath : discoverSourceRoots(inputPath)) {
            SourceRoot sourceRoot = new SourceRoot(sourceRootPath);

            List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();
            for (ParseResult<CompilationUnit> parseResult : parseResults) {
                if (!parseResult.isSuccessful()) {
                    throw new IllegalStateException("Failed to parse source tree: " + parseResult.getProblems());
                }
            }

            for (CompilationUnit compilationUnit : sourceRoot.getCompilationUnits()) {
                rewriteCompilationUnit(compilationUnit);
            }

            sourceRoot.saveAll();
        }
    }

    private static List<Path> discoverSourceRoots(Path inputPath) {
        List<Path> roots = new java.util.ArrayList<Path>();
        Path mainJava = inputPath.resolve("src/main/java");
        Path testJava = inputPath.resolve("src/test/java");
        if (Files.isDirectory(mainJava)) {
            roots.add(mainJava);
        }
        if (Files.isDirectory(testJava)) {
            roots.add(testJava);
        }
        if (roots.isEmpty() && Files.isDirectory(inputPath)) {
            roots.add(inputPath);
        }
        return roots;
    }

    private static boolean containsJavaFiles(Path path) {
        try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
            return stream.anyMatch(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to inspect source roots under " + path, e);
        }
    }

    private static void rewriteCompilationUnit(CompilationUnit compilationUnit) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : compilationUnit.findAll(ImportDeclaration.class)) {
            changed |= rewriteImportDeclaration(importDeclaration);
        }

        if (changed) {
            compilationUnit.addImport("jakarta.validation.Validation");
        }
    }

    private static boolean rewriteImportDeclaration(ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        if (name.startsWith(OLD_PACKAGE)) {
            importDeclaration.setName(StaticJavaParser.parseName(rewriteQualifiedName(name)));
            return true;
        }
        return false;
    }

    private static String rewriteQualifiedName(String value) {
        return NEW_PACKAGE + value.substring(OLD_PACKAGE.length());
    }
}
