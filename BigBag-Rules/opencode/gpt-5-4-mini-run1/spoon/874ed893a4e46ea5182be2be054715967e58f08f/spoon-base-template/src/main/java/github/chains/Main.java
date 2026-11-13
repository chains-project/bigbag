package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OBJECT_MAPPER = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String JSON_MAPPER = "com.fasterxml.jackson.databind.json.JsonMapper";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        File sourceDir = new File(args[0]);
        File outputDir = args.length > 1 ? new File(args[1]) : new File(sourceDir, "spoon-out");

        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.buildModel();

        Factory factory = launcher.getFactory();

        launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)).forEach(element -> {
            CtConstructorCall<?> call = (CtConstructorCall<?>) element;
            if (call.getType() == null || !OBJECT_MAPPER.equals(call.getType().getQualifiedName())) {
                return;
            }

            String replacement;
            if (call.getArguments().isEmpty()) {
                replacement = JSON_MAPPER + ".builder().build()";
            } else if (call.getArguments().size() == 1) {
                replacement = JSON_MAPPER + ".builder(" + ((CtExpression<?>) call.getArguments().get(0)).prettyprint() + ").build()";
            } else {
                return;
            }

            call.replace(factory.Code().createCodeSnippetExpression(replacement));
        });

        launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)).forEach(element -> {
            CtInvocation<?> invocation = (CtInvocation<?>) element;
            CtExecutableReference<?> exec = invocation.getExecutable();
            CtExpression<?> target = invocation.getTarget();
            if (exec == null || target == null || target.getType() == null) {
                return;
            }

            if (!OBJECT_MAPPER.equals(target.getType().getQualifiedName())) {
                return;
            }

            String name = exec.getSimpleName();
            if (!name.startsWith("enableDefaultTyping")) {
                return;
            }

            List<CtExpression<?>> argsList = new ArrayList<>(invocation.getArguments());
            argsList.add(0, factory.Code().createCodeSnippetExpression(
                "com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance"
            ));

            String replacement = buildReplacement(invocation.getTarget().prettyprint(), name, argsList);
            invocation.replace(factory.Code().createCodeSnippetExpression(replacement));
        });

        launcher.prettyprint();
    }

    private static String buildReplacement(String target, String oldName, List<CtExpression<?>> args) {
        String method = oldName.replace("enable", "activate");
        StringBuilder sb = new StringBuilder(target).append(".").append(method).append("(");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args.get(i).prettyprint());
        }
        return sb.append(")").toString();
    }
}
