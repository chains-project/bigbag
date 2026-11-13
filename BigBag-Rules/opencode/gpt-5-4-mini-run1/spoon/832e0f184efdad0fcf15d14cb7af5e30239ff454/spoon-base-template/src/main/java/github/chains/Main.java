package github.chains;

import java.io.File;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length == 2 ? new File(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(11);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.buildModel();

        List<CtInvocation> invocations = launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!"parseEnchantment".equals(invocation.getExecutable().getSimpleName())) {
                continue;
            }
            if (!invocation.getArguments().isEmpty()) {
                continue;
            }

            CtExpression<?> target = invocation.getTarget();
            if (!(target instanceof CtInvocation)) {
                continue;
            }

            CtInvocation<?> getInvocation = (CtInvocation<?>) target;
            if (!"get".equals(getInvocation.getExecutable().getSimpleName())) {
                continue;
            }
            if (!getInvocation.getArguments().isEmpty()) {
                continue;
            }

            CtExpression<?> matchTarget = getInvocation.getTarget();
            if (!(matchTarget instanceof CtInvocation)) {
                continue;
            }

            CtInvocation<?> matchInvocation = (CtInvocation<?>) matchTarget;
            if (!"matchXEnchantment".equals(matchInvocation.getExecutable().getSimpleName())) {
                continue;
            }
            if (matchInvocation.getArguments().size() != 1) {
                continue;
            }

            CtTypeReference<?> declaringType = matchInvocation.getExecutable().getDeclaringType();
            if (declaringType == null || !"XEnchantment".equals(declaringType.getSimpleName())) {
                continue;
            }

            CtElement replacement = launcher.getFactory().Code()
                    .createCodeSnippetExpression(getInvocation.toString() + ".getEnchant()");
            invocation.replace(replacement);
        }

        launcher.setSourceOutputDirectory(outputDir);
        launcher.prettyprint();
    }
}
