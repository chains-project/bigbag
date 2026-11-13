package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_HEXOF = "org.cactoos.text.HexOf";

    private static final String OLD_BYTESOF = "org.cactoos.io.BytesOf";

    private static final String OLD_LISTOF = "org.cactoos.list.ListOf";

    private static final String OLD_HEXOF_IMPORT = "import org.cactoos.text.HexOf;";

    private static final String OLD_BYTESOF_IMPORT = "import org.cactoos.io.BytesOf;";

    public static void main(final String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }
        final Path source = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path output = args.length == 2
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : source.resolveSibling(source.getFileName().toString() + "-spooned");
        if (Files.exists(output)) {
            deleteRecursively(output);
        }
        Files.createDirectories(output);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        addJavaSources(launcher, source);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        transform(launcher);
        launcher.prettyprint();
        cleanupImports(output);
        System.out.println("Transformed sources written to " + output);
    }

    private static void addJavaSources(final Launcher launcher, final Path source) throws IOException {
        try (var paths = Files.walk(source)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        }
    }

    private static void transform(final Launcher launcher) {
        final Factory factory = launcher.getFactory();
        final List<CtInvocation<?>> invocations = launcher.getModel().getElements(
            new TypeFilter<>(CtInvocation.class)
        );
        for (final CtInvocation<?> invocation : invocations) {
            if (!"asString".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            final CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtConstructorCall<?> hexof)) {
                continue;
            }
            if (!OLD_HEXOF.equals(hexof.getType().getQualifiedName())) {
                continue;
            }
            if (hexof.getArguments().size() != 1) {
                continue;
            }
            final CtExpression<?> bytes = hexof.getArguments().get(0);
            if (!(bytes instanceof CtConstructorCall<?> bytesof)) {
                continue;
            }
            if (!OLD_BYTESOF.equals(bytesof.getType().getQualifiedName())) {
                continue;
            }
            if (bytesof.getArguments().size() != 1) {
                continue;
            }
            final CtExpression<?> data = bytesof.getArguments().get(0);
            invocation.replace(
                factory.Code().createCodeSnippetExpression(
                    "java.util.HexFormat.of().formatHex(" + data + ")"
                )
            );
        }

        final List<CtConstructorCall<?>> listofs = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        );
        for (final CtConstructorCall<?> call : listofs) {
            if (call.getType() == null || !OLD_LISTOF.equals(call.getType().getQualifiedName())) {
                continue;
            }
            final String joined = call.getArguments().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
            call.replace(
                factory.Code().createCodeSnippetExpression("java.util.List.of(" + joined + ")")
            );
        }
    }

    private static void cleanupImports(final Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (final Path file : paths.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList())) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                content = content.replaceAll("(?m)^import "+OLD_HEXOF.replace(".", "\\.")+";\\R", "");
                content = content.replaceAll("(?m)^import "+OLD_BYTESOF.replace(".", "\\.")+";\\R", "");
                content = content.replaceAll("(?m)^import "+OLD_LISTOF.replace(".", "\\.")+";\\R", "");
                Files.writeString(file, content, StandardCharsets.UTF_8);
            }
        }
    }

    private static void deleteRecursively(final Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (final IOException err) {
                    throw new IllegalStateException(err);
                }
            });
        }
    }
}
