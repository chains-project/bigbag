package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_TYPE = "com.artipie.http.auth.Authentication.User";

    private static final String NEW_TYPE = "com.artipie.http.auth.AuthUser";

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }
        final Path input = Paths.get(args[0]);
        final Path output = args.length > 1 ? Paths.get(args[1]) : input;
        final Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setSourceOutputDirectory(output.toFile());
        launcher.buildModel();
        final Factory factory = launcher.getFactory();
        final CtTypeReference<?> newType = factory.Type().createReference(NEW_TYPE);
        final List<CtConstructorCall<?>> calls = launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        );
        for (final CtConstructorCall<?> call : calls) {
            final CtTypeReference<?> type = call.getType();
            if (type != null && OLD_TYPE.equals(type.getQualifiedName())) {
                final List<CtExpression<?>> arguments = call.getArguments();
                if (arguments.size() == 1) {
                    final CtConstructorCall<?> replacement = factory.Code().createConstructorCall(
                        newType.clone(),
                        arguments.get(0),
                        factory.Code().createLiteral("")
                    );
                    copyPosition(call, replacement);
                    call.replace(replacement);
                }
            }
        }
        for (final CtTypeReference<?> type : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (OLD_TYPE.equals(type.getQualifiedName())) {
                type.replace(newType.clone());
            }
        }
        launcher.prettyprint();
    }

    private static void copyPosition(final CtElement source, final CtElement target) {
        final SourcePosition position = source.getPosition();
        if (position != null && position.isValidPosition()) {
            target.setPosition(position);
        }
    }
}
