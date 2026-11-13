package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import spoon.MavenLauncher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String TARGET_DECLARING_TYPE =
            "org.sonarsource.sonarlint.core.analysis.api.AnalysisEngineConfiguration.Builder";
    private static final String TARGET_METHOD = "addEnabledLanguages";

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: source directory");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path projectRoot = findProjectRoot(sourceRoot);
        MavenLauncher launcher =
                new MavenLauncher(projectRoot.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        launcher.getEnvironment().setNoClasspath(false);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(sourceRoot.toString());
        launcher.addProcessor(new RemoveAddEnabledLanguagesProcessor());
        launcher.run();
    }

    private static Path findProjectRoot(Path sourceRoot) {
        Path current = sourceRoot;
        while (current != null) {
            if (current.resolve("pom.xml").toFile().isFile()) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalArgumentException("Could not locate pom.xml for " + sourceRoot);
    }

    static final class RemoveAddEnabledLanguagesProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null || !TARGET_METHOD.equals(executable.getSimpleName())) {
                return false;
            }
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            return declaringType != null && TARGET_DECLARING_TYPE.equals(declaringType.getQualifiedName());
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            if (invocation.getRoleInParent() == CtRole.STATEMENT) {
                invocation.delete();
                return;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target != null) {
                invocation.replace(target.clone());
            } else {
                invocation.delete();
            }
        }
    }
}
