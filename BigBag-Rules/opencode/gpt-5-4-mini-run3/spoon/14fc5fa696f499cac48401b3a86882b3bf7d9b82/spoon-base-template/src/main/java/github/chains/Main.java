package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
    private static final String FLUENT_TYPE = "org.flywaydb.core.api.configuration.FluentConfiguration";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        final Path input = Paths.get(args[0]).toAbsolutePath();
        final Path output = args.length > 1 ? Paths.get(args[1]).toAbsolutePath() : input.resolve("spoon-out");

        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setCopyResources(false);
        launcher.getEnvironment().setSourceOutputDirectory(output.toFile());
        final Path tempInput = prepareJavaOnlyCopy(input);
        addJavaSources(launcher, tempInput);
        launcher.buildModel();

        final CtTypeReference<?> flywayType = launcher.getFactory().Type().createReference(FLYWAY_TYPE);
        final CtTypeReference<?> fluentType = launcher.getFactory().Type().createReference(FLUENT_TYPE);

        for (CtMethod<?> method : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
            rewriteMethod(method, flywayType, fluentType, launcher);
        }

        launcher.prettyprint();
    }

    private static Path prepareJavaOnlyCopy(final Path input) {
        try {
            final Path temp = Files.createTempDirectory("spoon-input-");
            try (Stream<Path> paths = Files.walk(input)) {
                paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                final Path relative = input.relativize(path);
                                final Path target = temp.resolve(relative.toString());
                                Files.createDirectories(target.getParent());
                                Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                            } catch (final Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            return temp;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void addJavaSources(final Launcher launcher, final Path input) {
        try (Stream<Path> paths = Files.walk(input)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void rewriteMethod(final CtMethod<?> method, final CtTypeReference<?> flywayType,
                                      final CtTypeReference<?> fluentType, final Launcher launcher) {
        if (method.getBody() == null) {
            return;
        }

        final List<CtLocalVariable<?>> candidates = new ArrayList<>(method.getBody().getElements(new TypeFilter<>(CtLocalVariable.class)));
        for (CtLocalVariable<?> local : candidates) {
            if (!isFlywayConstructor(local, flywayType)) {
                continue;
            }
            rewriteFlywayLocal(method, local, fluentType, launcher);
        }
    }

    private static boolean isFlywayConstructor(final CtLocalVariable<?> local, final CtTypeReference<?> flywayType) {
        if (local.getType() == null || !FLYWAY_TYPE.equals(local.getType().getQualifiedName())) {
            return false;
        }
        return local.getDefaultExpression() instanceof CtInvocation<?> invocation
                && invocation.getExecutable() != null
                && invocation.getExecutable().getDeclaringType() != null
                && FLYWAY_TYPE.equals(invocation.getExecutable().getDeclaringType().getQualifiedName())
                && "<init>".equals(invocation.getExecutable().getSimpleName())
                && invocation.getArguments().isEmpty();
    }

    private static void rewriteFlywayLocal(final CtMethod<?> method, final CtLocalVariable<?> local,
                                           final CtTypeReference<?> fluentType, final Launcher launcher) {
        final String variableName = local.getSimpleName();
        final List<CtStatement> statements = new ArrayList<>(method.getBody().getStatements());

        for (CtStatement statement : statements) {
            final CtInvocation<?> invocation = asFlywaySetter(statement, variableName);
            if (invocation == null) {
                continue;
            }
            final String oldName = invocation.getExecutable().getSimpleName();
            final String rewritten = rewriteInvocation(invocation, oldName.equals("setClassLoader"));
            statement.replace(launcher.getFactory().Code().createCodeSnippetStatement(rewritten + ";"));
        }

        local.setType(fluentType);
        local.setDefaultExpression(launcher.getFactory().Code().createCodeSnippetExpression(buildConfigureExpression(method, variableName)));

        for (CtReturn<?> ctReturn : method.getBody().getElements(new TypeFilter<>(CtReturn.class))) {
            if (ctReturn.getReturnedExpression() instanceof CtVariableRead<?> read
                    && read.getVariable() != null
                    && variableName.equals(read.getVariable().getSimpleName())) {
                ctReturn.setReturnedExpression(launcher.getFactory().Code()
                        .createCodeSnippetExpression(variableName + ".load()"));
            }
        }
    }

    private static CtInvocation<?> asFlywaySetter(final CtStatement statement, final String variableName) {
        if (!(statement instanceof CtInvocation<?> invocation)) {
            return null;
        }
        if (invocation.getTarget() == null || invocation.getExecutable() == null) {
            return null;
        }
        if (!variableName.equals(invocation.getTarget().toString())) {
            return null;
        }
        final String name = invocation.getExecutable().getSimpleName();
        return name.startsWith("set") ? invocation : null;
    }

    private static String buildConfigureExpression(final CtMethod<?> method, final String variableName) {
        final CtInvocation<?> classLoaderSetter = findSetter(method, variableName, "setClassLoader");
        if (classLoaderSetter == null || classLoaderSetter.getArguments().isEmpty()) {
            return FLYWAY_TYPE + ".configure()";
        }
        return FLYWAY_TYPE + ".configure(" + classLoaderSetter.getArguments().get(0).toString() + ")";
    }

    private static CtInvocation<?> findSetter(final CtMethod<?> method, final String variableName, final String setterName) {
        for (CtStatement statement : method.getBody().getStatements()) {
            final CtInvocation<?> invocation = asFlywaySetter(statement, variableName);
            if (invocation != null && setterName.equals(invocation.getExecutable().getSimpleName())) {
                return invocation;
            }
        }
        return null;
    }

    private static String rewriteInvocation(final CtInvocation<?> invocation, final boolean classLoaderSetter) {
        final String receiver = invocation.getTarget().toString();
        final String methodName = invocation.getExecutable().getSimpleName();
        final String newName = classLoaderSetter ? "configure" : toFluentName(methodName);
        final String args = invocation.getArguments().stream().map(Object::toString).reduce((a, b) -> a + ", " + b).orElse("");
        return classLoaderSetter ? FLYWAY_TYPE + "." + newName + "(" + args + ")" : receiver + "." + newName + "(" + args + ")";
    }

    private static String toFluentName(final String oldName) {
        if (!oldName.startsWith("set") || oldName.length() <= 3) {
            return oldName;
        }
        return Character.toLowerCase(oldName.charAt(3)) + oldName.substring(4);
    }
}
