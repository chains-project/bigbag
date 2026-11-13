package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least one argument: source root");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputRoot = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceRoot;

        int replacements = 0;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<Path> javaFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path inputFile : javaFiles) {
                Path relative = sourceRoot.relativize(inputFile);
                Path outputFile = outputRoot.resolve(relative);
                CompilationUnit unit = StaticJavaParser.parse(inputFile);
                if (transformCompilationUnit(unit)) {
                    Files.createDirectories(outputFile.getParent());
                    Files.writeString(outputFile, unit.toString(), StandardCharsets.UTF_8);
                    replacements++;
                } else if (!sourceRoot.equals(outputRoot)) {
                    Files.createDirectories(outputFile.getParent());
                    Files.copy(inputFile, outputFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            if (replacements > 0) {
                writeHelperClass(outputRoot);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform sources", e);
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit unit) {
        boolean usesDigestUtils = hasDigestUtilsImport(unit);
        boolean[] changed = {false};

        unit.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"md5Hex".equals(call.getNameAsString())) {
                return;
            }

            if (!isDigestUtilsCall(call, usesDigestUtils)) {
                return;
            }

            List<Expression> args = new ArrayList<>(call.getArguments());
            MethodCallExpr replacement = new MethodCallExpr(
                    new NameExpr("Hashing"),
                    "md5Hex",
                    new com.github.javaparser.ast.NodeList<>(args)
            );
            call.replace(replacement);
            changed[0] = true;
        });

        if (changed[0]) {
            if (unit.getImports().stream().noneMatch(i -> i.getNameAsString().equals("github.chains.Hashing"))) {
                unit.addImport("github.chains.Hashing");
            }
            unit.getImports().removeIf(Main::isDigestUtilsImport);
        }

        return changed[0];
    }

    private static boolean isDigestUtilsCall(MethodCallExpr call, boolean usesDigestUtilsImport) {
        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            return scope.endsWith("DigestUtils");
        }
        return usesDigestUtilsImport;
    }

    private static boolean hasDigestUtilsImport(CompilationUnit unit) {
        return unit.getImports().stream().anyMatch(Main::isDigestUtilsImport);
    }

    private static boolean isDigestUtilsImport(ImportDeclaration importDeclaration) {
        String name = importDeclaration.getNameAsString();
        if (importDeclaration.isAsterisk()) {
            return false;
        }
        if (importDeclaration.isStatic()) {
            return name.equals("org.apache.commons.codec.digest.DigestUtils.md5Hex");
        }
        return name.equals("org.apache.commons.codec.digest.DigestUtils");
    }

    private static void writeHelperClass(Path outputRoot) throws IOException {
        Path helperFile = outputRoot.resolve(Paths.get("src/main/java/github/chains/Hashing.java"));
        Files.createDirectories(helperFile.getParent());
        String content = "package github.chains;\n\n"
                + "import java.io.IOException;\n"
                + "import java.io.InputStream;\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.security.MessageDigest;\n"
                + "import java.security.NoSuchAlgorithmException;\n\n"
                + "public final class Hashing {\n"
                + "    private Hashing() {}\n\n"
                + "    public static String md5Hex(String value) {\n"
                + "        if (value == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        return md5Hex(value.getBytes(StandardCharsets.UTF_8));\n"
                + "    }\n\n"
                + "    public static String md5Hex(byte[] value) {\n"
                + "        if (value == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        return toHex(digest(value));\n"
                + "    }\n\n"
                + "    public static String md5Hex(InputStream value) throws IOException {\n"
                + "        if (value == null) {\n"
                + "            return null;\n"
                + "        }\n"
                + "        MessageDigest digest = newDigest();\n"
                + "        byte[] buffer = new byte[8192];\n"
                + "        int read;\n"
                + "        while ((read = value.read(buffer)) != -1) {\n"
                + "            digest.update(buffer, 0, read);\n"
                + "        }\n"
                + "        return toHex(digest.digest());\n"
                + "    }\n\n"
                + "    private static byte[] digest(byte[] value) {\n"
                + "        MessageDigest digest = newDigest();\n"
                + "        return digest.digest(value);\n"
                + "    }\n\n"
                + "    private static MessageDigest newDigest() {\n"
                + "        try {\n"
                + "            return MessageDigest.getInstance(\"MD5\");\n"
                + "        } catch (NoSuchAlgorithmException e) {\n"
                + "            throw new IllegalStateException(e);\n"
                + "        }\n"
                + "    }\n\n"
                + "    private static String toHex(byte[] value) {\n"
                + "        StringBuilder builder = new StringBuilder(value.length * 2);\n"
                + "        for (byte b : value) {\n"
                + "            builder.append(Character.forDigit((b >> 4) & 0xF, 16));\n"
                + "            builder.append(Character.forDigit(b & 0xF, 16));\n"
                + "        }\n"
                + "        return builder.toString();\n"
                + "    }\n"
                + "}\n";
        Files.writeString(helperFile, content, StandardCharsets.UTF_8);
    }
}
