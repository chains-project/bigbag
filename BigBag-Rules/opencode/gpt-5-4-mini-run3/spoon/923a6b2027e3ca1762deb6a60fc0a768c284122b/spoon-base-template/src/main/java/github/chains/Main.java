package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtImport;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory path");
        }

        final Path input = Paths.get(args[0]);
        final Path output = args.length > 1
            ? Paths.get(args[1])
            : input.resolveSibling(input.getFileName() + "-transformed");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new AbstractProcessor<CtConstructorCall<?>>() {
            @Override
            public void process(CtConstructorCall<?> call) {
                if (call.getType() == null) {
                    return;
                }
                if (!"org.hamcrest.core.IsAnything".equals(
                    call.getType().getQualifiedName())) {
                    return;
                }

                final String replacement;
                if (call.getArguments().isEmpty()) {
                    replacement = "(org.hamcrest.Matcher) org.hamcrest.Matchers.anything()";
                } else if (call.getArguments().size() == 1) {
                    replacement = String.format(
                        "(org.hamcrest.Matcher) org.hamcrest.Matchers.anything(%s)",
                        call.getArguments().get(0)
                    );
                } else {
                    replacement = "(org.hamcrest.Matcher) org.hamcrest.Matchers.anything()";
                }
                call.replace(
                    call.getFactory().Code().createCodeSnippetExpression(replacement)
                );
            }
        });
        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport ctImport) {
                final String ref = ctImport.getReference() == null
                    ? ""
                    : ctImport.getReference().toString();
                if ("org.hamcrest.core.IsAnything".equals(ref)) {
                    ctImport.delete();
                }
            }
        });
        launcher.run();
    }
}
