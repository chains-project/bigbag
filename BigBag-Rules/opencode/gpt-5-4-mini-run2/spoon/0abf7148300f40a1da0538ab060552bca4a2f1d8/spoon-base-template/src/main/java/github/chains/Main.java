package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Path input = Paths.get(args[0]);
        Path output = Paths.get(args[1]);
        Files.createDirectories(output);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        List<CtInvocation<?>> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!isTargetSetLineWidth(invocation)) {
                continue;
            }

            CtExpression<?> argument = invocation.getArguments().get(0);
            CtExpression<?> replacement = factory.Code().createCodeSnippetExpression(
                    "java.lang.Float.valueOf((float)(" + argument + "))");
            invocation.getArguments().set(0, replacement);
        }

        launcher.prettyprint();
    }

    private static boolean isTargetSetLineWidth(CtInvocation<?> invocation) {
        if (!"setLineWidth".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        if (invocation.getArguments().size() != 1) {
            return false;
        }

        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        if (declaringType == null) {
            return true;
        }

        String qualifiedName = declaringType.getQualifiedName();
        return qualifiedName.startsWith("net.sf.jasperreports.engine.");
    }
}
