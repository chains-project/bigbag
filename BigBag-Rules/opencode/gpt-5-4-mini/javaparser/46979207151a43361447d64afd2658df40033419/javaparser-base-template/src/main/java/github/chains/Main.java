package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final Set<String> JAXB_STRATEGY_TYPES = Set.of(
            "org.jvnet.jaxb2_commons.lang.JAXBCopyStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBEqualsStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBHashCodeStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBMergeStrategy",
            "org.jvnet.jaxb2_commons.lang.JAXBToStringStrategy"
    );

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-directory>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan source tree: " + sourceRoot, e);
        }
    }

    private static void rewriteFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);

            String original = Files.readString(path);
            String rewritten = original;
            List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);
            calls.sort((left, right) -> rangeStart(right.getRange().orElseThrow()) - rangeStart(left.getRange().orElseThrow()));

            for (MethodCallExpr call : calls) {
                if (!"getInstance".equals(call.getNameAsString())) {
                    continue;
                }

                Expression scope = call.getScope().orElse(null);
                if (scope == null || !isJaxbStrategyScope(scope)) {
                    continue;
                }

                Range range = call.getRange().orElse(null);
                if (range == null) {
                    continue;
                }

                int begin = positionToOffset(rewritten, range.begin.line, range.begin.column);
                int end = positionToOffset(rewritten, range.end.line, range.end.column) + 1;
                rewritten = rewritten.substring(0, begin) + scope + ".INSTANCE" + rewritten.substring(end);
            }

            if (!rewritten.equals(original)) {
                Files.writeString(path, rewritten);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite file: " + path, e);
        }
    }

    private static int rangeStart(Range range) {
        return range.begin.line * 100000 + range.begin.column;
    }

    private static int positionToOffset(String text, int line, int column) {
        int currentLine = 1;
        int offset = 0;
        while (currentLine < line && offset < text.length()) {
            if (text.charAt(offset++) == '\n') {
                currentLine++;
            }
        }
        return offset + column - 1;
    }

    private static boolean isJaxbStrategyScope(Expression scope) {
        String text = scope.toString();
        for (String typeName : JAXB_STRATEGY_TYPES) {
            String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1);
            if (text.equals(simpleName) || text.endsWith("." + simpleName)) {
                return true;
            }
        }

        return false;
    }
}
