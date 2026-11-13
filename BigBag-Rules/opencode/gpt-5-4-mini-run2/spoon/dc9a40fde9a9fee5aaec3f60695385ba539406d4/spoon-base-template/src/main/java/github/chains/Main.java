package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String ASYNC_RPC_RESULT = "org.apache.dubbo.rpc.AsyncRpcResult";
    private static final Set<String> TARGET_METHODS = Set.of("setFuture", "setCompatibleFuture");

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Path.of(args[0]);
        Path outputDir = args.length > 1 ? Path.of(args[1]) : Paths.get(sourceDir.toString() + "-spoon-out");
        if (outputDir.equals(sourceDir)) {
            outputDir = Paths.get(sourceDir.toString() + "-spoon-out");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        addSourceRoots(launcher, sourceDir);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new AsyncFutureArgumentProcessor());
        launcher.run();
    }

    private static void addSourceRoots(Launcher launcher, Path sourceDir) {
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> {
                        String normalized = path.toString().replace('\\', '/');
                        return normalized.endsWith("/src/main/java") || normalized.endsWith("/src/test/java");
                    })
                    .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to scan source roots", ex);
        }
    }

    private static final class AsyncFutureArgumentProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            if (!TARGET_METHODS.contains(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return false;
            }

            if (candidate.getArguments().size() != 1) {
                return false;
            }

            CtExpression<?> argument = candidate.getArguments().get(0);
            CtTypeReference<?> argumentType = argument.getType();
            return argumentType != null && ASYNC_RPC_RESULT.equals(argumentType.getQualifiedName());
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            CtExpression<?> argument = invocation.getArguments().get(0);
            invocation.getArguments().set(0,
                    getFactory().Code().createCodeSnippetExpression(argument + ".getResponseFuture()"));
        }
    }
}
