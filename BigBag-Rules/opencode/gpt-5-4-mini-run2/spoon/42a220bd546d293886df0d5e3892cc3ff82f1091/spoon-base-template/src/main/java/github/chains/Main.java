package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static final String OLD_TYPE = "org.apache.commons.codec.digest.DigestUtils";
    private static final String OLD_METHOD = "md5Hex";
    private static final String HELPER_PACKAGE = "generated";
    private static final String HELPER_CLASS = "Md5Support";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceDir> [outputDir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir;

        Files.createDirectories(outputDir);

        Launcher launcher = new Launcher();
        addSources(launcher, inputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        Map<Path, List<Edit>> editsByFile = new LinkedHashMap<>();
        AtomicInteger rewrites = new AtomicInteger();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isOldMd5HexCall(invocation)) {
                SourcePosition position = invocation.getPosition();
                SourcePosition argumentPosition = invocation.getArguments().get(0).getPosition();
                if (position == null || position.getFile() == null || argumentPosition == null || argumentPosition.getFile() == null) {
                    continue;
                }
                Path file = position.getFile().toPath().toAbsolutePath().normalize();
                String argumentSource = readSlice(file, argumentPosition.getSourceStart(), argumentPosition.getSourceEnd());
                String replacement = HELPER_PACKAGE + "." + HELPER_CLASS + ".md5Hex(" + argumentSource + ")";
                editsByFile.computeIfAbsent(file, unused -> new ArrayList<>())
                        .add(new Edit(position.getSourceStart(), position.getSourceEnd(), replacement));
                rewrites.incrementAndGet();
            }
        }

        if (rewrites.get() > 0) {
            applyEdits(editsByFile);
            writeHelper(outputDir);
            stripOldImports(outputDir);
        }

        System.out.println("Rewrote " + rewrites.get() + " md5Hex call(s).");
    }

    private static void addSources(Launcher launcher, Path inputDir) {
        Path mainJava = inputDir.resolve("src/main/java");
        Path testJava = inputDir.resolve("src/test/java");
        if (Files.isDirectory(mainJava)) {
            addJavaFiles(launcher, mainJava);
        }
        if (Files.isDirectory(testJava)) {
            addJavaFiles(launcher, testJava);
        }
        if (!Files.isDirectory(mainJava) && !Files.isDirectory(testJava)) {
            launcher.addInputResource(inputDir.toString());
        }
    }

    private static void addJavaFiles(Launcher launcher, Path root) {
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void applyEdits(Map<Path, List<Edit>> editsByFile) throws IOException {
        for (Map.Entry<Path, List<Edit>> entry : editsByFile.entrySet()) {
            Path file = entry.getKey();
            String source = Files.readString(file, StandardCharsets.UTF_8);
            List<Edit> edits = entry.getValue();
            edits.sort(Comparator.comparingInt((Edit edit) -> edit.start).reversed());
            for (Edit edit : edits) {
                source = source.substring(0, edit.start) + edit.replacement + source.substring(edit.end + 1);
            }
            Files.writeString(file, source, StandardCharsets.UTF_8);
        }
    }

    private static String readSlice(Path file, int start, int end) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        return source.substring(start, end + 1);
    }

    private static boolean isOldMd5HexCall(CtInvocation<?> invocation) {
        if (invocation == null || invocation.getArguments().size() != 1) {
            return false;
        }
        if (!Objects.equals(invocation.getExecutable().getSimpleName(), OLD_METHOD)) {
            return false;
        }
        if (invocation.getExecutable().getDeclaringType() == null) {
            return false;
        }
        String declaringType = invocation.getExecutable().getDeclaringType().getQualifiedName();
        return OLD_TYPE.equals(declaringType);
    }

    private static void writeHelper(Path outputDir) throws IOException {
        Path baseDir = Files.isDirectory(outputDir.resolve("src/main/java"))
                ? outputDir.resolve("src/main/java")
                : outputDir;
        Path helperDir = baseDir.resolve(HELPER_PACKAGE);
        Files.createDirectories(helperDir);

        Path helperFile = helperDir.resolve(HELPER_CLASS + ".java");
        String helperSource = "package " + HELPER_PACKAGE + ";\n\n"
                + "import java.nio.charset.StandardCharsets;\n"
                + "import java.security.MessageDigest;\n"
                + "import java.security.NoSuchAlgorithmException;\n\n"
                + "public final class " + HELPER_CLASS + " {\n"
                + "    private " + HELPER_CLASS + "() {\n"
                + "    }\n\n"
                + "    public static String md5Hex(String value) {\n"
                + "        try {\n"
                + "            MessageDigest digest = MessageDigest.getInstance(\"MD5\");\n"
                + "            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));\n"
                + "            StringBuilder hex = new StringBuilder(bytes.length * 2);\n"
                + "            for (byte b : bytes) {\n"
                + "                int unsigned = b & 0xFF;\n"
                + "                if (unsigned < 16) {\n"
                + "                    hex.append('0');\n"
                + "                }\n"
                + "                hex.append(Integer.toHexString(unsigned));\n"
                + "            }\n"
                + "            return hex.toString();\n"
                + "        } catch (NoSuchAlgorithmException e) {\n"
                + "            throw new IllegalStateException(\"MD5 not available\", e);\n"
                + "        }\n"
                + "    }\n"
                + "}\n";

        Files.writeString(helperFile, helperSource, StandardCharsets.UTF_8);
    }

    private static void stripOldImports(Path outputDir) throws IOException {
        try (var paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path, StandardCharsets.UTF_8);
                    String updated = source.replace("import " + OLD_TYPE + ";\n", "");
                    if (!updated.equals(source)) {
                        Files.writeString(path, updated, StandardCharsets.UTF_8);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static final class Edit {
        private final int start;
        private final int end;
        private final String replacement;

        private Edit(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }
}
