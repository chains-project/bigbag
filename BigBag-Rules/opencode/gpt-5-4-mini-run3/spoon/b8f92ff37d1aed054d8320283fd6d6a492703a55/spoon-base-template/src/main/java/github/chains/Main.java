package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        CtTypeReference<?> resolverType = factory.Type().createReference("org.apache.maven.surefire.api.testset.TestListResolver");

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getExecutable() != null
                    && "getWildcard".equals(invocation.getExecutable().getSimpleName())
                    && invocation.getArguments().isEmpty()
                    && invocation.getTarget() instanceof CtTypeAccess) {
                CtTypeAccess<?> target = (CtTypeAccess<?>) invocation.getTarget();
                if (target.getAccessedType() == null
                        || !"org.apache.maven.surefire.api.testset.TestListResolver".equals(target.getAccessedType().getQualifiedName())) {
                    continue;
                }

                CtConstructorCall<?> ctor = factory.Code().createConstructorCall(
                        resolverType,
                        factory.Code().createLiteral("*.class")
                );
                invocation.replace(ctor);
            }
        }

        launcher.prettyprint();
    }
}
