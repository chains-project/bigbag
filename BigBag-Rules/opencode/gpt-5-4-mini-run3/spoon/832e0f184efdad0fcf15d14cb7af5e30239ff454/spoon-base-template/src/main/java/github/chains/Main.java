package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String OLD_TYPE = "com.cryptomorin.xseries.XEnchantment";
    private static final String OLD_METHOD = "parseEnchantment";
    private static final String NEW_METHOD = "getEnchant";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Paths.get(args[0]);
        Path outputDir = args.length > 1 ? Paths.get(args[1]) : sourceDir.resolveSibling(sourceDir.getFileName() + "-transformed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());

        launcher.buildModel();

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!OLD_METHOD.equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }

            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType != null && !OLD_TYPE.equals(declaringType.getQualifiedName())) {
                continue;
            }

            invocation.getExecutable().setSimpleName(NEW_METHOD);
        }

        launcher.prettyprint();
    }
}
