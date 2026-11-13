package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String CONSTRUCTOR_FQN = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String SAFE_CONSTRUCTOR_FQN = "org.yaml.snakeyaml.constructor.SafeConstructor";
    private static final String REPRESENTER_FQN = "org.yaml.snakeyaml.representer.Representer";
    private static final String DUMPER_OPTIONS_FQN = "org.yaml.snakeyaml.DumperOptions";
    private static final String LOADER_OPTIONS_FQN = "org.yaml.snakeyaml.LoaderOptions";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        transformConstructorCalls(launcher);
        transformConstructors(launcher);

        launcher.prettyprint();
    }

    private static void transformConstructorCalls(Launcher launcher) {
        launcher.getModel().getElements(new TypeFilter<>(spoon.reflect.code.CtConstructorCall.class)).forEach(call -> {
            CtTypeReference<?> declaringType = call.getExecutable() == null ? null : call.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return;
            }

            String owner = declaringType.getQualifiedName();
            if (REPRESENTER_FQN.equals(owner) && call.getArguments().isEmpty()) {
                call.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                        "new " + REPRESENTER_FQN + "(new " + DUMPER_OPTIONS_FQN + "())"));
            } else if (CONSTRUCTOR_FQN.equals(owner) && call.getArguments().size() == 1) {
                call.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                        "new " + CONSTRUCTOR_FQN + "(" + call.getArguments().get(0) + ", new " + LOADER_OPTIONS_FQN + "())"));
            } else if (SAFE_CONSTRUCTOR_FQN.equals(owner) && call.getArguments().isEmpty()) {
                call.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                        "new " + SAFE_CONSTRUCTOR_FQN + "(new " + LOADER_OPTIONS_FQN + "())"));
            }
        });
    }

    private static void transformConstructors(Launcher launcher) {
        launcher.getModel().getElements(new TypeFilter<>(CtConstructor.class)).forEach(ctor -> {
            CtType<?> type = ctor.getParent(CtType.class);
            if (type == null || type.getSuperclass() == null) {
                return;
            }

            String superType = type.getSuperclass().getQualifiedName();
            if (REPRESENTER_FQN.equals(superType)) {
                ensureSuperCall(ctor, "super(new org.yaml.snakeyaml.DumperOptions())");
            } else if (CONSTRUCTOR_FQN.equals(superType) || SAFE_CONSTRUCTOR_FQN.equals(superType)) {
                ensureSuperCall(ctor, "super(\"__SNAKEYAML_SUPER__\")");
            }
        });
    }

    private static void ensureSuperCall(CtConstructor<?> ctor, String template) {
        if (ctor.getBody() == null) {
            return;
        }

        if (ctor.getBody().getStatements().isEmpty()) {
            ctor.getBody().insertBegin(ctor.getFactory().Code().createCodeSnippetStatement(resolveSuperTemplate(template, null)));
            return;
        }

        String first = ctor.getBody().getStatements().get(0).toString().trim();
        if (!first.startsWith("super(")) {
            if (template.contains("DumperOptions")) {
                ctor.getBody().insertBegin(ctor.getFactory().Code().createCodeSnippetStatement(template));
            }
            return;
        }

        if (first.contains("DumperOptions") || first.contains("LoaderOptions")) {
            return;
        }

        String updated = resolveSuperTemplate(template, first);
        ctor.getBody().getStatements().get(0)
                .replace(ctor.getFactory().Code().createCodeSnippetStatement(updated));
    }

    private static String resolveSuperTemplate(String template, String existing) {
        if ("super(\"__SNAKEYAML_SUPER__\")".equals(template)) {
            if (existing == null) {
                return "super(new org.yaml.snakeyaml.LoaderOptions())";
            }
            String trimmed = existing.endsWith(";") ? existing.substring(0, existing.length() - 1) : existing;
            int close = trimmed.lastIndexOf(')');
            if (close > 0) {
                return trimmed.substring(0, close) + ", new org.yaml.snakeyaml.LoaderOptions()" + trimmed.substring(close);
            }
            return "super(new org.yaml.snakeyaml.LoaderOptions())";
        }
        return template;
    }
}
