package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Path inputDir = Paths.get(args[0]);
        Path outputDir = Paths.get(args[1]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
        launcher.addInputResource(inputDir.toString());
        launcher.buildModel();

        launcher.getModel()
            .getElements(new TypeFilter<>(CtInvocation.class))
            .stream()
            .filter(invocation -> invocation.getExecutable() != null)
            .filter(invocation -> "org.apache.fop.apps.FopFactory".equals(
                invocation.getExecutable().getDeclaringType() == null
                    ? null
                    : invocation.getExecutable().getDeclaringType().getQualifiedName()))
            .filter(invocation -> "newInstance".equals(invocation.getExecutable().getSimpleName()))
            .filter(invocation -> invocation.getArguments().isEmpty())
            .forEach(invocation -> invocation.replace(
                launcher.getFactory().Code().createCodeSnippetExpression(
                    "org.apache.fop.apps.FopFactory.newInstance(new java.io.File(\".\").toURI())")));

        launcher.prettyprint();
    }
}
