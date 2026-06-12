package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String LOGBACK_LOGGER = "ch.qos.logback.classic.Logger";
    private static final String LOGBACK_LEVEL = "ch.qos.logback.classic.Level";
    private static final String SET_LEVEL_SIGNATURE = "ch.qos.logback.classic.Logger#setLevel(ch.qos.logback.classic.Level)";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> [outputDir]");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = args.length == 2 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-out");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isSetLevelOnLogbackLogger(invocation)) {
                continue;
            }

            CtStatement statement = invocation.getParent(CtStatement.class);
            if (statement != null) {
                statement.delete();
            }
        }

        launcher.prettyprint();
    }

    private static boolean isSetLevelOnLogbackLogger(CtInvocation<?> invocation) {
        if (!"setLevel".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }

        if (invocation.getExecutable().getParameters().size() != 1) {
            return false;
        }

        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        if (declaringType == null || !LOGBACK_LOGGER.equals(declaringType.getQualifiedName())) {
            return false;
        }

        CtTypeReference<?> parameterType = invocation.getExecutable().getParameters().get(0);
        if (parameterType == null || !LOGBACK_LEVEL.equals(parameterType.getQualifiedName())) {
            return false;
        }

        if (!hasQualifiedType(invocation.getTarget(), LOGBACK_LOGGER)) {
            return false;
        }

        return invocation.getArguments().size() == 1 && hasQualifiedType(invocation.getArguments().get(0), LOGBACK_LEVEL);
    }

    private static boolean hasQualifiedType(Object node, String expectedType) {
        if (node == null) {
            return false;
        }

        if (node instanceof CtInvocation<?> invocation) {
            CtTypeReference<?> type = invocation.getType();
            return type != null && expectedType.equals(type.getQualifiedName());
        }

        if (node instanceof spoon.reflect.code.CtExpression<?> expression) {
            CtTypeReference<?> type = expression.getType();
            return type != null && expectedType.equals(type.getQualifiedName());
        }

        return false;
    }
}
