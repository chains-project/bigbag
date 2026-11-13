package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_HEX_TYPE = "org.cactoos.text.HexOf";

    private static final String OLD_BYTES_TYPE = "org.cactoos.io.BytesOf";

    private static final String OLD_LIST_TYPE = "org.cactoos.list.ListOf";

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }
        final Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        final Path output = args.length > 1
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : input.resolveSibling(input.getFileName() + "-rewritten");
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        try (Stream<Path> files = Files.walk(input)) {
            files.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final java.io.IOException err) {
            throw new IllegalStateException("Unable to read input sources", err);
        }
        launcher.setSourceOutputDirectory(output.toFile());
        final var model = launcher.buildModel();
        rewriteTransforms(
            launcher.getFactory(),
            new ArrayList<>(model.getElements(new TypeFilter<>(CtInvocation.class))),
            new ArrayList<>(model.getElements(new TypeFilter<>(CtConstructorCall.class))),
            new ArrayList<>(model.getElements(new TypeFilter<>(CtCompilationUnit.class)))
        );
        launcher.prettyprint();
    }

    private static void rewriteTransforms(
        final Factory factory,
        final List<CtInvocation<?>> invocations,
        final List<CtConstructorCall<?>> constructors,
        final List<CtCompilationUnit> compilationUnits
    ) {
        rewriteHexEncoding(factory, invocations);
        rewriteListOf(factory, constructors);
        cleanupImports(compilationUnits);
    }

    private static void rewriteHexEncoding(
        final Factory factory,
        final List<CtInvocation<?>> invocations
    ) {
        for (final CtInvocation<?> invocation : invocations) {
            if (!"asString".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            final CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtConstructorCall<?> hexCall)) {
                continue;
            }
            if (!isType(hexCall, OLD_HEX_TYPE) || hexCall.getArguments().size() != 1) {
                continue;
            }
            final CtExpression<?> bytesTarget = hexCall.getArguments().get(0);
            if (!(bytesTarget instanceof CtConstructorCall<?> bytesCall)) {
                continue;
            }
            if (!isType(bytesCall, OLD_BYTES_TYPE) || bytesCall.getArguments().size() != 1) {
                continue;
            }
            final String source = bytesCall.getArguments().get(0).prettyprint();
            invocation.replace(
                factory.Code().createCodeSnippetExpression(
                    "java.util.HexFormat.of().formatHex(" + source + ")"
                )
            );
        }
    }

    private static void rewriteListOf(
        final Factory factory,
        final List<CtConstructorCall<?>> constructors
    ) {
        for (final CtConstructorCall<?> ctor : constructors) {
            if (!isType(ctor, OLD_LIST_TYPE)) {
                continue;
            }
            final String joined = ctor.getArguments().stream()
                .map(CtExpression::prettyprint)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
            ctor.replace(
                factory.Code().createCodeSnippetExpression("java.util.List.of(" + joined + ")")
            );
        }
    }

    private static void cleanupImports(final List<CtCompilationUnit> units) {
        for (final CtCompilationUnit unit : units) {
            unit.getImports().removeIf(Main::isObsoleteImport);
        }
    }

    private static boolean isObsoleteImport(final CtImport imp) {
        if (imp.getReference() == null) {
            return false;
        }
        final String qname = imp.getReference().toString();
        return OLD_HEX_TYPE.equals(qname)
            || OLD_BYTES_TYPE.equals(qname)
            || OLD_LIST_TYPE.equals(qname);
    }

    private static boolean isType(final CtConstructorCall<?> call, final String qname) {
        return call.getType() != null && qname.equals(call.getType().getQualifiedName());
    }
}
