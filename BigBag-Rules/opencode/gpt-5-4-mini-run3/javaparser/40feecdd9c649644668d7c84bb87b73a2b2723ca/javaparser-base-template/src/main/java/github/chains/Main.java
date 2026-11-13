package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.TryStmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String IO_EXCEPTION = "IOException";
    private static final String JODA_DATETIME_FORMATTER = "org.joda.time.format.DateTimeFormatter";
    private static final String PRINT_TO = "printTo";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path javaFile) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(Files.readString(javaFile, StandardCharsets.UTF_8));
            boolean changed = false;

            for (TryStmt tryStmt : cu.findAll(TryStmt.class)) {
                if (!hasIOExceptionCatch(tryStmt)) {
                    continue;
                }
                if (!containsJodaPrintTo(tryStmt.getTryBlock())) {
                    continue;
                }

                tryStmt.replace(tryStmt.getTryBlock().clone());
                changed = true;
            }

            if (changed) {
                cu.getImports().removeIf(Main::isIOExceptionImport);
                Files.writeString(javaFile, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + javaFile, e);
        }
    }

    private static boolean hasIOExceptionCatch(TryStmt tryStmt) {
        return tryStmt.getCatchClauses().stream().map(CatchClause::getParameter)
                .map(parameter -> parameter.getType().asString())
                .anyMatch(type -> IO_EXCEPTION.equals(type) || type.endsWith("." + IO_EXCEPTION));
    }

    private static boolean isIOExceptionImport(ImportDeclaration importDeclaration) {
        return importDeclaration.getNameAsString().equals("java.io.IOException");
    }

    private static boolean containsJodaPrintTo(BlockStmt blockStmt) {
        return blockStmt.findAll(MethodCallExpr.class).stream().anyMatch(Main::isTargetPrintToCall);
    }

    private static boolean isTargetPrintToCall(MethodCallExpr call) {
        if (!PRINT_TO.equals(call.getNameAsString()) || call.getArguments().size() != 2) {
            return false;
        }

        return call.findCompilationUnit()
                .flatMap(cu -> cu.getImports().stream()
                        .filter(ImportDeclaration::isAsterisk)
                        .findFirst())
                .map(ignore -> false)
                .orElse(true)
                && call.findCompilationUnit()
                .map(cu -> cu.getImports().stream().anyMatch(Main::isDateTimeFormatterImport))
                .orElse(false);
    }

    private static boolean isDateTimeFormatterImport(ImportDeclaration importDeclaration) {
        return importDeclaration.getNameAsString().equals(JODA_DATETIME_FORMATTER);
    }
}
