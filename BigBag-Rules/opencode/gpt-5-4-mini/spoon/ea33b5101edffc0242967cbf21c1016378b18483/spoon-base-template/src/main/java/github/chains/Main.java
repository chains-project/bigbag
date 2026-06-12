package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String FLYWAY_FQN = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(args[0]);
        launcher.buildModel();

        new FlywayTransformer(launcher).apply();

        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.prettyprint();
    }

    private static final class FlywayTransformer {
        private final Launcher launcher;

        private FlywayTransformer(Launcher launcher) {
            this.launcher = launcher;
        }

        private void apply() {
            for (CtMethod<?> method : this.launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
                rewriteMethod(method);
            }
        }

        private void rewriteMethod(CtMethod<?> method) {
            if (method.getBody() == null) {
                return;
            }

            CtBlock<?> body = method.getBody();
            List<CtStatement> statements = new ArrayList<>(body.getStatements());
            Map<String, FlywayConfig> configs = new LinkedHashMap<>();
            List<CtStatement> removals = new ArrayList<>();

            for (int i = 0; i < statements.size(); i++) {
                CtStatement statement = statements.get(i);
                CtLocalVariable<?> local = asFlywayLocal(statement);
                if (local == null) {
                    continue;
                }

                String variableName = local.getSimpleName();
                FlywayConfig config = new FlywayConfig();
                config.hasInitializer = true;

                for (int j = i + 1; j < statements.size(); j++) {
                    CtStatement later = statements.get(j);
                    CtInvocation<?> invocation = asSetterInvocation(later, variableName);
                    if (invocation == null) {
                        continue;
                    }
                    config.consume(invocation);
                    removals.add(later);
                }

                if (!config.hasAnySetter()) {
                    continue;
                }

                local.setDefaultExpression(this.launcher.getFactory().Code().createCodeSnippetExpression(config.buildExpression()));
                configs.put(variableName, config);
            }

            for (CtStatement removal : removals) {
                removal.delete();
            }
        }

        private CtLocalVariable<?> asFlywayLocal(CtStatement statement) {
            if (!(statement instanceof CtLocalVariable<?>)) {
                return null;
            }
            CtLocalVariable<?> local = (CtLocalVariable<?>) statement;
            if (local.getType() == null || !FLYWAY_FQN.equals(local.getType().getQualifiedName())) {
                return null;
            }
            if (local.getDefaultExpression() == null || !local.getDefaultExpression().toString().startsWith("new Flyway()")) {
                return null;
            }
            return local;
        }

        private CtInvocation<?> asSetterInvocation(CtStatement statement, String variableName) {
            if (!(statement instanceof CtInvocation<?>)) {
                return null;
            }
            CtInvocation<?> invocation = (CtInvocation<?>) statement;
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || executable.getSimpleName() == null) {
                return null;
            }
            if (!FlywayConfig.isSupportedSetter(executable.getSimpleName())) {
                return null;
            }
            CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtVariableRead<?>)) {
                return null;
            }
            CtVariableReference<?> variable = ((CtVariableRead<?>) target).getVariable();
            if (variable == null || !variableName.equals(variable.getSimpleName())) {
                return null;
            }
            return invocation;
        }

        private static final class FlywayConfig {
            private final List<String> parts = new ArrayList<>();
            private String classLoaderArgument;
            private boolean hasInitializer;

            private static boolean isSupportedSetter(String name) {
                return "setDataSource".equals(name)
                    || "setClassLoader".equals(name)
                    || "setLocations".equals(name)
                    || "setValidateOnMigrate".equals(name);
            }

            private void consume(CtInvocation<?> invocation) {
                String name = invocation.getExecutable().getSimpleName();
                String argument = invocation.getArguments().isEmpty() ? "" : invocation.getArguments().get(0).clone().toString();

                if ("setClassLoader".equals(name)) {
                    this.classLoaderArgument = argument;
                    return;
                }

                if ("setDataSource".equals(name)) {
                    this.parts.add(".dataSource(" + argument + ")");
                } else if ("setLocations".equals(name)) {
                    this.parts.add(".locations(" + argument + ")");
                } else if ("setValidateOnMigrate".equals(name)) {
                    this.parts.add(".validateOnMigrate(" + argument + ")");
                }
            }

            private boolean hasAnySetter() {
                return this.classLoaderArgument != null || !this.parts.isEmpty();
            }

            private String buildExpression() {
                StringBuilder builder = new StringBuilder();
                builder.append("org.flywaydb.core.Flyway.configure(");
                if (this.classLoaderArgument != null) {
                    builder.append(this.classLoaderArgument);
                }
                builder.append(")");
                for (String part : this.parts) {
                    builder.append(part);
                }
                builder.append(".load()");
                return builder.toString();
            }
        }
    }
}
