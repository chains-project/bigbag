package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String SNKEYAML_REPRESENTER = "org.yaml.snakeyaml.representer.Representer";
    private static final String SNKEYAML_CONSTRUCTOR = "org.yaml.snakeyaml.constructor.Constructor";

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length == 2 ? Paths.get(args[1]) : input;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());

        CtModel model = launcher.buildModel();
        var factory = launcher.getFactory();

        rewriteRepresenterConstructorCalls(model, factory);
        rewriteConstructorSuperCalls(model, factory);
        insertRepresenterSuperCalls(model, factory);

        launcher.prettyprint();
    }

    private static void rewriteRepresenterConstructorCalls(CtModel model, spoon.reflect.factory.Factory factory) {
        List<CtConstructorCall> calls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getExecutable() == null ? null : call.getExecutable().getDeclaringType();
            if (type == null || !SNKEYAML_REPRESENTER.equals(type.getQualifiedName())) {
                continue;
            }
            if (call.getArguments().size() != 0) {
                continue;
            }
            call.replace(factory.Code().createCodeSnippetExpression(
                    "new org.yaml.snakeyaml.representer.Representer(new org.yaml.snakeyaml.DumperOptions())"));
        }
    }

    private static void rewriteConstructorSuperCalls(CtModel model, spoon.reflect.factory.Factory factory) {
        List<CtConstructorCall> calls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getExecutable() == null ? null : call.getExecutable().getDeclaringType();
            if (type == null || !SNKEYAML_CONSTRUCTOR.equals(type.getQualifiedName())) {
                continue;
            }
            if (call.getArguments().size() != 1) {
                continue;
            }
            String firstArg = call.getArguments().get(0).toString();
            call.replace(factory.Code().createCodeSnippetStatement(
                    "super(" + firstArg + ", new org.yaml.snakeyaml.LoaderOptions())"));
        }
    }

    private static void insertRepresenterSuperCalls(CtModel model, spoon.reflect.factory.Factory factory) {
        List<CtConstructor<?>> constructors = model.getElements(new TypeFilter<>(CtConstructor.class));
        for (CtConstructor<?> constructor : constructors) {
            CtType<?> parent = constructor.getParent(CtType.class);
            if (!(parent instanceof CtClass<?> ctClass)) {
                continue;
            }
            if (!extendsType(ctClass.getSuperclass(), SNKEYAML_REPRESENTER)) {
                continue;
            }

            if (!constructor.getBody().getStatements().isEmpty()) {
                CtStatement first = constructor.getBody().getStatements().get(0);
                if (first instanceof CtConstructorCall) {
                    continue;
                }
            }

            constructor.getBody().insertBegin(factory.Code().createCodeSnippetStatement(
                    "super(new org.yaml.snakeyaml.DumperOptions())"));
        }
    }

    private static boolean extendsType(CtTypeReference<?> type, String fqcn) {
        for (CtTypeReference<?> current = type; current != null; current = current.getSuperclass()) {
            if (fqcn.equals(current.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }
}
