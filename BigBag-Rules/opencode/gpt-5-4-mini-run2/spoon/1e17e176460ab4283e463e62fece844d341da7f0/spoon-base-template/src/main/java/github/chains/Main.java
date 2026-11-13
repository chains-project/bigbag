package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.Collections;

public class Main {
    private static final String CONSTRUCTOR_FQN = "org.yaml.snakeyaml.constructor.Constructor";
    private static final String REPRESENTER_FQN = "org.yaml.snakeyaml.representer.Representer";
    private static final String LOADER_OPTIONS_FQN = "org.yaml.snakeyaml.LoaderOptions";
    private static final String DUMPER_OPTIONS_FQN = "org.yaml.snakeyaml.DumperOptions";
    private static final String INTROSPECTION_EXCEPTION_FQN = "java.beans.IntrospectionException";

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
            System.exit(1);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new SnakeYamlConstructorProcessor());
        launcher.addProcessor(new SnakeYamlRepresenterProcessor());
        launcher.addProcessor(new SnakeYamlRepresenterConstructorProcessor());
        launcher.run();
    }

    private static final class SnakeYamlConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> call) {
            CtTypeReference<?> type = call.getType();
            if (type == null) {
                return;
            }

            if (CONSTRUCTOR_FQN.equals(type.getQualifiedName()) && call.getArguments().size() == 1) {
                CtTypeReference<?> loaderOptionsType = getFactory().Type().createReference(LOADER_OPTIONS_FQN);
                CtConstructorCall<?> loaderOptions = getFactory().Code().createConstructorCall(loaderOptionsType);
                ArrayList newArguments = new ArrayList(call.getArguments());
                newArguments.add(loaderOptions);
                call.setArguments(newArguments);
                return;
            }

            if (REPRESENTER_FQN.equals(type.getQualifiedName()) && call.getArguments().isEmpty()) {
                CtTypeReference<?> dumperOptionsType = getFactory().Type().createReference(DUMPER_OPTIONS_FQN);
                CtConstructorCall<?> dumperOptions = getFactory().Code().createConstructorCall(dumperOptionsType);
                call.setArguments(new ArrayList<>(Collections.singletonList(dumperOptions)));
                return;
            }

            if ("org.yaml.snakeyaml.Yaml".equals(type.getQualifiedName()) && call.getArguments().size() == 4) {
                CtTypeReference<?> loaderOptionsType = getFactory().Type().createReference(LOADER_OPTIONS_FQN);
                CtConstructorCall<?> loaderOptions = getFactory().Code().createConstructorCall(loaderOptionsType);
                ArrayList newArguments = new ArrayList(call.getArguments());
                newArguments.add(3, loaderOptions);
                call.setArguments(newArguments);
            }
        }
    }

    private static final class SnakeYamlRepresenterProcessor extends AbstractProcessor<CtMethod<?>> {
        @Override
        public void process(CtMethod<?> method) {
            if (isRepresenterSubclass(method)) {
                method.getThrownTypes().removeIf(t -> INTROSPECTION_EXCEPTION_FQN.equals(t.getQualifiedName()));
            }
        }

        private boolean isRepresenterSubclass(CtMethod<?> method) {
            if (method.getDeclaringType() == null) {
                return false;
            }
            CtTypeReference<?> superclass = method.getDeclaringType().getSuperclass();
            return superclass != null && REPRESENTER_FQN.equals(superclass.getQualifiedName());
        }
    }

    private static final class SnakeYamlRepresenterConstructorProcessor extends AbstractProcessor<CtConstructor<?>> {
        @Override
        public void process(CtConstructor<?> constructor) {
            CtTypeReference<?> declaringType = constructor.getDeclaringType() == null ? null : constructor.getDeclaringType().getReference();
            if (declaringType == null) {
                return;
            }

            CtTypeReference<?> superclass = constructor.getDeclaringType().getSuperclass();
            if (superclass == null || !REPRESENTER_FQN.equals(superclass.getQualifiedName())) {
                return;
            }

            CtBlock<?> body = constructor.getBody();
            if (body == null) {
                return;
            }

            if (body.getStatements().isEmpty() || !body.getStatements().get(0).toString().startsWith("super(")) {
                body.insertBegin(getFactory().createCodeSnippetStatement("super(new " + DUMPER_OPTIONS_FQN + "())"));
            }
        }
    }
}
