package github.chains;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtExecutableReference;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
            System.exit(1);
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-rewritten");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new TinspinDependencyRewriteProcessor());
        launcher.run();
    }

    private static final class TinspinDependencyRewriteProcessor extends AbstractProcessor<CtElement> {

        @Override
        public void process(CtElement element) {
            if (element instanceof CtTypeReference) {
                rewriteTypeReference((CtTypeReference<?>) element);
            }
            if (element instanceof CtInvocation) {
                rewriteInvocation((CtInvocation<?>) element);
            }
        }

        private void rewriteTypeReference(CtTypeReference<?> typeRef) {
            String qn = typeRef.getQualifiedName();
            if ("org.tinspin.index.PointIndex".equals(qn)) {
                typeRef.replace(typeRef.getFactory().Type().createReference("org.tinspin.index.PointMap"));
            } else if ("org.tinspin.index.PointDistanceFunction".equals(qn)) {
                typeRef.replace(typeRef.getFactory().Type().createReference("org.tinspin.index.PointDistance"));
            } else if ("org.tinspin.index.PointEntryDist".equals(qn)) {
                typeRef.replace(typeRef.getFactory().Type().createReference("org.tinspin.index.Index.PointEntryKnn"));
            }
        }

        private void rewriteInvocation(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                return;
            }

            if ("query1NN".equals(executable.getSimpleName())) {
                executable.setSimpleName("query1nn");
            }

            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType != null && "org.tinspin.index.kdtree.KDTree".equals(declaringType.getQualifiedName())
                    && "create".equals(executable.getSimpleName()) && invocation.getArguments().size() == 2) {
                invocation.getArguments().remove(1);
            }
        }
    }
}
