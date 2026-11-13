package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Paths.get(args[0]).toAbsolutePath().normalize();
        Path output = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : input.resolveSibling(input.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.addProcessor(new MinaSslCompatibilityProcessor());
        launcher.run();
    }

    private static final class MinaSslCompatibilityProcessor extends AbstractProcessor<CtElement> {
        private static final String SSL_FILTER = "org.apache.mina.filter.ssl.SslFilter";

        @Override
        public void process(CtElement element) {
            if (element instanceof CtConstructorCall<?> constructorCall) {
                fixSslFilterConstructor(constructorCall);
            } else if (element instanceof CtInvocation<?> invocation) {
                fixRemovedInvocation(invocation);
            }
        }

        private void fixSslFilterConstructor(CtConstructorCall<?> constructorCall) {
            CtExecutableReference<?> executable = constructorCall.getExecutable();
            CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
            if (declaringType == null || !SSL_FILTER.equals(declaringType.getQualifiedName())) {
                return;
            }

            List<?> arguments = constructorCall.getArguments();
            if (arguments.size() == 2) {
                constructorCall.getArguments().remove(1);
            }
        }

        private void fixRemovedInvocation(CtInvocation<?> invocation) {
            String methodName = invocation.getExecutable() == null ? null : invocation.getExecutable().getSimpleName();
            if (!"setUseClientMode".equals(methodName) && !"initiateHandshake".equals(methodName)) {
                return;
            }

            CtStatement statement = invocation.getParent(CtStatement.class);
            if (statement != null) {
                statement.delete();
            } else {
                invocation.delete();
            }
        }

    }
}
