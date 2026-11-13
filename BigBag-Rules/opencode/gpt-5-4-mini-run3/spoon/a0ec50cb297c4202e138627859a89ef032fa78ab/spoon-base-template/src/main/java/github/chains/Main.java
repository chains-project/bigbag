package github.chains;

import java.nio.file.Path;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path");
        }
        final Path source = Path.of(args[0]);
        final Path output = args.length > 1 ? Path.of(args[1]) : source;
        final Launcher launcher = new Launcher();
        launcher.addInputResource(source.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addProcessor(new AbstractProcessor<CtCompilationUnit>() {
            @Override
            public void process(final CtCompilationUnit unit) {
                final java.util.List<spoon.reflect.declaration.CtImport> imports = new java.util.ArrayList<>(unit.getImports());
                imports.removeIf(
                    imp -> {
                        final String name = imp.getReference() == null ? "" : imp.getReference().toString();
                        return "org.cactoos.io.BytesOf".equals(name) || "org.cactoos.text.HexOf".equals(name);
                    }
                );
                unit.setImports(imports);
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(final CtInvocation<?> invocation) {
                if (!"asString".equals(invocation.getExecutable().getSimpleName())) {
                    return;
                }
                final CtExpression<?> target = invocation.getTarget();
                if (!(target instanceof CtConstructorCall<?>)) {
                    return;
                }
                final CtConstructorCall<?> hexOf = (CtConstructorCall<?>) target;
                if (!hasTypeName(hexOf.getType(), "org.cactoos.text.HexOf") || hexOf.getArguments().size() != 1) {
                    return;
                }
                final CtExpression<?> bytesExpr = hexOf.getArguments().get(0);
                if (!(bytesExpr instanceof CtConstructorCall<?>)) {
                    return;
                }
                final CtConstructorCall<?> bytesOf = (CtConstructorCall<?>) bytesExpr;
                if (!hasTypeName(bytesOf.getType(), "org.cactoos.io.BytesOf") || bytesOf.getArguments().size() != 1) {
                    return;
                }
                final Factory factory = invocation.getFactory();
                final String replacement = "java.util.HexFormat.of().formatHex(" + bytesOf.getArguments().get(0) + ")";
                invocation.replace(factory.Code().createCodeSnippetExpression(replacement));
            }

            private boolean hasTypeName(final CtTypeReference<?> type, final String name) {
                return type != null && name.equals(type.getQualifiedName());
            }
        });
        launcher.run();
    }
}
