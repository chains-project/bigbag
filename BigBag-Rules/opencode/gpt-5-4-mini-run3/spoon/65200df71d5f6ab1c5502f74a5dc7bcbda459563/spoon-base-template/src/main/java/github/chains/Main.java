package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String BUILDER_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";
    private static final String LANGUAGE_TYPE =
            "org.sonarsource.sonarlint.core.commons.Language";
    private static final String METHOD_NAME = "addEnabledLanguages";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory path");
        }

        Path inputDir = Path.of(args[0]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(inputDir.toAbsolutePath().toString());
        launcher.buildModel();

        List<CtInvocation<?>> matches = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));

        for (CtInvocation<?> invocation : matches) {
            invocation = (CtInvocation<?>) invocation;
            if (!isTargetInvocation(invocation)) {
                continue;
            }
            rewriteInvocationArgument(invocation);
        }
    }

    private static boolean isTargetInvocation(CtInvocation<?> invocation) {
        return METHOD_NAME.equals(invocation.getExecutable().getSimpleName())
                && invocation.getTarget() != null
                && invocation.getTarget().getType() != null
                && BUILDER_TYPE.equals(invocation.getTarget().getType().getQualifiedName())
                && invocation.getArguments().size() == 1;
    }

    private static void rewriteInvocationArgument(CtInvocation<?> invocation) throws IOException {
        CtExpression<?> argument = invocation.getArguments().get(0);
        SourcePosition argumentPosition = argument.getPosition();
        if (argumentPosition == null || !argumentPosition.isValidPosition()) {
            return;
        }

        Path file = argumentPosition.getFile().toPath();
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String original = source.substring(argumentPosition.getSourceStart(), argumentPosition.getSourceEnd() + 1);
        String replacement = original + ".toArray(new " + LANGUAGE_TYPE + "[0])";
        String updated =
                source.substring(0, argumentPosition.getSourceStart())
                        + replacement
                        + source.substring(argumentPosition.getSourceEnd() + 1);
        Files.writeString(file, updated, StandardCharsets.UTF_8);
    }

    private static int startOffset(CtInvocation<?> invocation) {
        SourcePosition position = invocation.getPosition();
        return position != null && position.isValidPosition() ? position.getSourceStart() : Integer.MAX_VALUE;
    }
}
