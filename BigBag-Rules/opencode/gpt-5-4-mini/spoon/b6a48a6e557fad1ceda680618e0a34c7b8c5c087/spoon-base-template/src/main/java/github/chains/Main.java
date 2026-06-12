package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_FLYWAY = "org.flywaydb.core.Flyway";
    private static final String NEW_FLYWAY = "org.flywaydb.core.Flyway";
    private static final String CONFIGURE = "org.flywaydb.core.Flyway.configure";

    private static final Map<String, String> BUILDER_METHODS = new HashMap<>();

    static {
        BUILDER_METHODS.put("setDataSource", "dataSource");
        BUILDER_METHODS.put("setLocations", "locations");
        BUILDER_METHODS.put("setValidateOnMigrate", "validateOnMigrate");
    }

    private static final class Edit {
        final int startLine;
        final int endLine;
        final List<String> replacement;

        Edit(final int startLine, final int endLine, final List<String> replacement) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.replacement = replacement;
        }
    }

    private static final class Plan {
        final Path file;
        final int declarationStart;
        final int declarationEnd;
        final String variableName;
        final boolean isFinal;
        String classLoaderArg;
        final List<String> builderCalls = new ArrayList<>();
        final List<Edit> edits = new ArrayList<>();

        Plan(final Path file, final int declarationStart, final int declarationEnd, final String variableName, final boolean isFinal) {
            this.file = file;
            this.declarationStart = declarationStart;
            this.declarationEnd = declarationEnd;
            this.variableName = variableName;
            this.isFinal = isFinal;
        }
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <inputDir>");
        }

        final File inputDir = new File(args[0]);
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        final Map<Path, List<Edit>> editsByFile = new LinkedHashMap<>();

        for (final CtLocalVariable<?> variable : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (!isOldFlyway(variable.getType()) || !isNoArgFlywayConstructor(variable.getDefaultExpression())) {
                continue;
            }
            if (variable.getPosition() == null || variable.getPosition().getFile() == null) {
                continue;
            }

            final CtStatement declarationStatement = variable.getParent(CtStatement.class);
            final CtBlock<?> block = declarationStatement == null ? null : declarationStatement.getParent(CtBlock.class);
            if (block == null) {
                continue;
            }

            final List<CtStatement> statements = block.getStatements();
            final int index = statements.indexOf(declarationStatement);
            if (index < 0) {
                continue;
            }

            final Path file = variable.getPosition().getFile().toPath();
            final Plan plan = new Plan(file, variable.getPosition().getLine(), variable.getPosition().getEndLine(), variable.getSimpleName(), variable.isFinal());

            for (int i = index + 1; i < statements.size(); i++) {
                final CtStatement statement = statements.get(i);
                if (statement.getPosition() == null) {
                    continue;
                }

                if (statement instanceof CtInvocation) {
                    final CtInvocation<?> invocation = (CtInvocation<?>) statement;
                    if (!plan.variableName.equals(variableName(invocation.getTarget()))) {
                        break;
                    }

                    final String method = invocation.getExecutable().getSimpleName();
                    if ("setClassLoader".equals(method)) {
                        plan.classLoaderArg = invocation.getArguments().isEmpty() ? null : invocation.getArguments().get(0).toString();
                        plan.edits.add(new Edit(statement.getPosition().getLine(), statement.getPosition().getEndLine(), List.of()));
                        continue;
                    }

                    final String builder = BUILDER_METHODS.get(method);
                    if (builder == null) {
                        break;
                    }

                    plan.builderCalls.add(builder + "(" + arguments(invocation) + ")");
                    plan.edits.add(new Edit(statement.getPosition().getLine(), statement.getPosition().getEndLine(), List.of()));
                    continue;
                }

                break;
            }

            final String init = buildInit(plan);
            plan.edits.add(new Edit(plan.declarationStart, plan.declarationEnd, List.of(buildDeclarationLine(plan, init))));
            editsByFile.computeIfAbsent(file, k -> new ArrayList<>()).addAll(plan.edits);
        }

        for (final Map.Entry<Path, List<Edit>> entry : editsByFile.entrySet()) {
            applyEdits(entry.getKey(), entry.getValue());
        }
    }

    private static String buildInit(final Plan plan) {
        final StringBuilder sb = new StringBuilder();
        sb.append(plan.classLoaderArg == null ? CONFIGURE + "()" : CONFIGURE + "(" + plan.classLoaderArg + ")");
        for (final String call : plan.builderCalls) {
            sb.append('.').append(call);
        }
        sb.append(".load()");
        return sb.toString();
    }

    private static String buildDeclarationLine(final Plan plan, final String init) {
        final String prefix = plan.isFinal ? "final " : "";
        return prefix + NEW_FLYWAY + " " + plan.variableName + " = " + init + ";";
    }

    private static void applyEdits(final Path file, final List<Edit> edits) throws IOException {
        final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        final List<Edit> sorted = edits.stream().sorted(Comparator.comparingInt((Edit e) -> e.startLine).reversed()).collect(Collectors.toList());
        for (final Edit edit : sorted) {
            final int start = Math.max(1, edit.startLine);
            final int end = Math.max(start, edit.endLine);
            for (int line = end; line >= start; line--) {
                if (line - 1 < lines.size()) {
                    lines.remove(line - 1);
                }
            }
            lines.addAll(start - 1, edit.replacement);
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static boolean isOldFlyway(final CtTypeReference<?> type) {
        return type != null && OLD_FLYWAY.equals(type.getQualifiedName());
    }

    private static boolean isNoArgFlywayConstructor(final CtExpression<?> expression) {
        return expression instanceof CtConstructorCall && ((CtConstructorCall<?>) expression).getArguments().isEmpty();
    }

    private static String variableName(final CtExpression<?> expression) {
        if (!(expression instanceof CtVariableAccess)) {
            return null;
        }
        return ((CtVariableAccess<?>) expression).getVariable().getSimpleName();
    }

    private static String arguments(final CtInvocation<?> invocation) {
        return invocation.getArguments().stream().map(Object::toString).collect(Collectors.joining(", "));
    }
}
