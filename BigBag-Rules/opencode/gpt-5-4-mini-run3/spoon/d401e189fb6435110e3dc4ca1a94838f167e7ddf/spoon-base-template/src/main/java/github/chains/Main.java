package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String LOGGER_FACTORY = "org.slf4j.LoggerFactory";
    private static final String ROOT_LOGGER_FQN = "org.slf4j.Logger";
    private static final String BACKEND_LOGGER_FQN = "ch.qos.logback.classic.Logger";
    private static final String LEVEL_FQN = "ch.qos.logback.classic.Level";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected: <input-dir> <output-dir>");
        }

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(Paths.get(args[0]).toString());
        launcher.setSourceOutputDirectory(Path.of(args[1]).toFile());

        launcher.buildModel();
        apply(launcher);
        launcher.prettyprint();
    }

    private static void apply(Launcher launcher) {
        final List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!isLegacySetLevelCall(invocation)) {
                continue;
            }

            final CtStatement statement = invocation.getParent(CtStatement.class);
            if (statement == null) {
                continue;
            }

            final String loggerExpr = normalizeLoggerName(extractLoggerName(invocation));
            final String levelName = extractLevelName(invocation.getArguments().get(0).toString());
            statement.replace(invocation.getFactory().Code().createCodeSnippetStatement(buildReplacement(loggerExpr, levelName)));
        }
    }

    private static boolean isLegacySetLevelCall(CtInvocation<?> invocation) {
        final CtExecutableReference<?> executable = invocation.getExecutable();
        final CtTypeReference<?> declaringType = executable.getDeclaringType();
        return declaringType != null
                && LOGGER_FACTORY.equals(declaringType.getQualifiedName())
                && "getLogger".equals(executable.getSimpleName())
                && invocation.getArguments().size() == 1
                && isSetLevelOnBackendLogger(invocation);
    }

    private static boolean isSetLevelOnBackendLogger(CtInvocation<?> invocation) {
        final CtElement parent = invocation.getParent();
        return parent != null
                && parent.toString().contains(BACKEND_LOGGER_FQN)
                && parent.getParent() != null
                && parent.getParent().toString().contains("setLevel");
    }

    private static String extractLoggerName(CtInvocation<?> invocation) {
        final String text = invocation.toString();
        final int start = text.indexOf("getLogger(");
        if (start < 0) {
            return "org.slf4j.Logger.ROOT_LOGGER_NAME";
        }
        final int argStart = start + "getLogger(".length();
        final int argEnd = text.indexOf(')', argStart);
        if (argEnd < 0) {
            return "org.slf4j.Logger.ROOT_LOGGER_NAME";
        }
        return text.substring(argStart, argEnd).trim();
    }

    private static String normalizeLoggerName(String loggerExpr) {
        if (loggerExpr.equals("Logger.ROOT_LOGGER_NAME") || loggerExpr.equals("ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME")) {
            return "org.slf4j.Logger.ROOT_LOGGER_NAME";
        }
        return loggerExpr;
    }

    private static String extractLevelName(String levelExpr) {
        final int dot = levelExpr.lastIndexOf('.');
        return dot >= 0 ? levelExpr.substring(dot + 1) : levelExpr;
    }

    private static String buildReplacement(String loggerExpr, String levelName) {
        return "try {"
                + "Object __factory = org.slf4j.LoggerFactory.getILoggerFactory();"
                + "java.lang.reflect.Method __getLogger = __factory.getClass().getMethod(\"getLogger\", String.class);"
                + "Object __logger = __getLogger.invoke(__factory, " + loggerExpr + ");"
                + "Class<?> __levelClass = Class.forName(\"" + LEVEL_FQN + "\");"
                + "java.lang.reflect.Method __setLevel = __logger.getClass().getMethod(\"setLevel\", __levelClass);"
                + "Object __level = __levelClass.getField(\"" + levelName + "\").get(null);"
                + "__setLevel.invoke(__logger, __level);"
                + "} catch (ReflectiveOperationException e) { throw new RuntimeException(e); }";
    }
}
