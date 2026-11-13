package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.ImportDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        ParserConfiguration config = new ParserConfiguration();
        StaticJavaParser.setConfiguration(config);

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            transformFile(path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to transform " + path, e);
                        }
                    });
            Path pom = root.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                transformPom(pom);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree", e);
        }
    }

    private static void transformFile(Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(original);
        } catch (ParseProblemException ex) {
            return;
        }

        boolean hasOkioStaticImport = cu.getImports().stream().anyMatch(Main::isOkioStaticImport);

        cu.findAll(MethodCallExpr.class).forEach(call -> rewriteOkioCalls(call, hasOkioStaticImport));

        String updated = cu.toString();
        if (!updated.equals(original)) {
            Files.writeString(path, updated, StandardCharsets.UTF_8);
        }
    }

    private static void transformPom(Path path) throws IOException {
        String original = Files.readString(path, StandardCharsets.UTF_8);
        if (!original.contains("com.squareup.okio") || !original.contains("<kotlin.version>")) {
            return;
        }

        String updated = original.replaceAll(
                "<kotlin\\.version>[^<]+</kotlin\\.version>",
                "<kotlin.version>1.8.22</kotlin.version>");

        if (!updated.equals(original)) {
            Files.writeString(path, updated, StandardCharsets.UTF_8);
        }
    }

    private static boolean isOkioStaticImport(ImportDeclaration importDeclaration) {
        if (!importDeclaration.isStatic()) {
            return false;
        }
        String name = importDeclaration.getNameAsString();
        return "com.squareup.okio.Okio".equals(name) || name.startsWith("com.squareup.okio.Okio.");
    }

    private static void rewriteOkioCalls(MethodCallExpr call, boolean hasOkioStaticImport) {
        String scopeName = call.getScope().map(Expression::toString).orElse(null);
        String methodName = call.getNameAsString();

        if (isOkioUtilityScope(scopeName) || (hasOkioStaticImport && call.getScope().isEmpty())) {
            if (call.getArguments().size() == 1 && isReceiverFactory(methodName)) {
                Expression receiver = call.getArgument(0).clone();
                MethodCallExpr replacement = new MethodCallExpr(receiver, methodName);
                call.replace(replacement);
            }
            if (call.getArguments().size() == 1 && isByteStringFactory(scopeName, methodName)) {
                Expression receiver = call.getArgument(0).clone();
                String replacementName = "encodeUtf8".equals(methodName) ? "encodeUtf8" : "toByteString";
                MethodCallExpr replacement = new MethodCallExpr(receiver, replacementName);
                call.replace(replacement);
            }
        }
    }

    private static boolean isOkioUtilityScope(String scopeName) {
        return "Okio".equals(scopeName) || "com.squareup.okio.Okio".equals(scopeName);
    }

    private static boolean isReceiverFactory(String methodName) {
        return FACTORY_METHODS.containsKey(methodName);
    }

    private static boolean isByteStringFactory(String scopeName, String methodName) {
        return ("ByteString".equals(scopeName) || "com.squareup.okio.ByteString".equals(scopeName)
                || "ByteString.Companion".equals(scopeName) || "com.squareup.okio.ByteString.Companion".equals(scopeName))
                && ("encodeUtf8".equals(methodName) || "of".equals(methodName));
    }

    private static final Map<String, String> FACTORY_METHODS = new HashMap<>();

    static {
        FACTORY_METHODS.put("buffer", "buffer");
        FACTORY_METHODS.put("source", "source");
        FACTORY_METHODS.put("sink", "sink");
        FACTORY_METHODS.put("appendingSink", "appendingSink");
    }
}
