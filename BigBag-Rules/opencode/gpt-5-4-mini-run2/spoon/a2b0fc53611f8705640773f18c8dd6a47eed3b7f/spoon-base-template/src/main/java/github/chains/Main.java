package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final String OLD_TYPE = "org.flywaydb.core.Flyway";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected one or more input source directories and an output directory");
        }

        String outputDir = args[args.length - 1];
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        for (int i = 0; i < args.length - 1; i++) {
            launcher.addInputResource(args[i]);
        }
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        for (int i = 0; i < args.length - 1; i++) {
            ensureOutputDirectories(Path.of(args[i]), Path.of(outputDir));
        }

        CtModel model = launcher.getModel();
        transform(model);
        launcher.prettyprint();
    }

    private static void ensureOutputDirectories(Path inputRoot, Path outputRoot) {
        try {
            if (!Files.exists(inputRoot)) {
                return;
            }
            Files.walk(inputRoot)
                    .filter(Files::isDirectory)
                    .forEach(dir -> {
                        try {
                            Files.createDirectories(outputRoot.resolve(inputRoot.relativize(dir)));
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare output directories", e);
        }
    }

    private static void transform(CtModel model) {
        for (CtLocalVariable<?> localVariable : model.getElements(new TypeFilter<>(CtLocalVariable.class))) {
            if (!isOldFlywayVariable(localVariable)) {
                continue;
            }

            CtConstructorCall<?> constructorCall = asConstructorCall(localVariable.getDefaultExpression());
            if (constructorCall == null || !constructorCall.getArguments().isEmpty()) {
                continue;
            }

            List<CtInvocation<?>> setters = collectAdjacentSetters(localVariable);
            if (setters.isEmpty()) {
                continue;
            }

            String replacement = buildReplacementExpression(setters);
            if (replacement == null) {
                continue;
            }

            localVariable.setDefaultExpression(localVariable.getFactory().Code().createCodeSnippetExpression(replacement));
            setters.forEach(CtElement::delete);
        }
    }

    private static boolean isOldFlywayVariable(CtLocalVariable<?> localVariable) {
        CtTypeReference<?> type = localVariable.getType();
        if (type == null || !OLD_TYPE.equals(type.getQualifiedName())) {
            return false;
        }
        return asConstructorCall(localVariable.getDefaultExpression()) != null;
    }

    private static CtConstructorCall<?> asConstructorCall(CtExpression<?> expression) {
        if (expression instanceof CtConstructorCall<?>) {
            return (CtConstructorCall<?>) expression;
        }
        return null;
    }

    private static List<CtInvocation<?>> collectAdjacentSetters(CtLocalVariable<?> localVariable) {
        List<CtInvocation<?>> setters = new ArrayList<>();
        CtElement parent = localVariable.getParent();
        if (!(parent instanceof CtBlock<?> block)) {
            return setters;
        }

        List<CtStatement> statements = new ArrayList<>(block.getStatements());
        int index = statements.indexOf(localVariable);
        if (index < 0) {
            return setters;
        }

        for (int i = index + 1; i < statements.size(); i++) {
            CtStatement statement = statements.get(i);
            if (!(statement instanceof CtInvocation<?> invocation) || !isSetterOnVariable(invocation, localVariable)) {
                break;
            }
            setters.add(invocation);
        }
        return setters;
    }

    private static boolean isSetterOnVariable(CtInvocation<?> invocation, CtLocalVariable<?> variable) {
        if (invocation.getTarget() == null || invocation.getExecutable() == null) {
            return false;
        }
        CtElement target = invocation.getTarget();
        return target instanceof CtVariableAccess<?> access
                && access.getVariable() != null
                && variable.getReference().equals(access.getVariable());
    }

    private static String buildReplacementExpression(List<CtInvocation<?>> setters) {
        String classLoaderArg = extractClassLoaderArg(setters);
        StringBuilder chain = new StringBuilder();

        for (CtInvocation<?> invocation : setters) {
            String methodName = invocation.getExecutable().getSimpleName();
            if ("setClassLoader".equals(methodName)) {
                continue;
            }

            String newMethod = toBuilderMethodName(methodName);
            if (newMethod == null) {
                return null;
            }
            if (chain.length() == 0) {
                chain.append("org.flywaydb.core.Flyway.");
                if (classLoaderArg != null) {
                    chain.append("configure(").append(classLoaderArg).append(")");
                } else {
                    chain.append("configure()");
                }
            }
            chain.append('.').append(newMethod).append('(');
            for (int i = 0; i < invocation.getArguments().size(); i++) {
                if (i > 0) {
                    chain.append(", ");
                }
                chain.append(invocation.getArguments().get(i).toString());
            }
            chain.append(')');
        }

        if (chain.length() == 0) {
            return null;
        }

        chain.append(".load()");
        return chain.toString();
    }

    private static String extractClassLoaderArg(List<CtInvocation<?>> setters) {
        for (CtInvocation<?> invocation : setters) {
            if (!"setClassLoader".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            if (invocation.getArguments().size() != 1) {
                return null;
            }
            return invocation.getArguments().get(0).toString();
        }
        return null;
    }

    private static String toBuilderMethodName(String setterName) {
        if (setterName == null || !setterName.startsWith("set") || setterName.length() <= 3) {
            return null;
        }
        String suffix = setterName.substring(3);
        return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
    }
}
