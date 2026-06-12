package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {
    private static final String RPC_CONTEXT = "org.apache.dubbo.rpc.RpcContext";
    private static final String RPC_CONTEXT_ATTACHMENT = "org.apache.dubbo.rpc.RpcContextAttachment";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Path.of(args[0]);
        Path outputDir = args.length > 1 ? Path.of(args[1]) : sourceDir.resolveSibling(sourceDir.getFileName() + "-rewritten");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.addProcessor(new DubboRpcContextProcessor());
        launcher.run();

        rewriteGeneratedSources(outputDir);
    }

    private static final class DubboRpcContextProcessor extends AbstractProcessor<CtElement> {
        @Override
        public boolean isToBeProcessed(CtElement element) {
            return element instanceof CtInvocation || element instanceof CtTypeReference;
        }

        @Override
        public void process(CtElement element) {
            if (element instanceof CtInvocation) {
                rewriteInvocation((CtInvocation<?>) element);
            } else if (element instanceof CtTypeReference) {
                rewriteTypeReference((CtTypeReference<?>) element);
            }
        }

        private void rewriteInvocation(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                return;
            }

            if (invocation.getArguments().size() != 0) {
                return;
            }

            if ("getClientAttachment".equals(executable.getSimpleName())) {
                executable.setSimpleName("getContext");
            } else if ("getServerAttachment".equals(executable.getSimpleName())) {
                executable.setSimpleName("getServerContext");
            }
        }

        private void rewriteTypeReference(CtTypeReference<?> typeReference) {
            if (!RPC_CONTEXT_ATTACHMENT.equals(typeReference.getQualifiedName())) {
                return;
            }

            typeReference.replace(typeReference.getFactory().Type().createReference(RPC_CONTEXT));
        }
    }

    private static void rewriteGeneratedSources(Path outputDir) {
        try (Stream<Path> paths = Files.walk(outputDir)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to post-process generated sources", e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String updated = content
                    .replace("import org.apache.dubbo.rpc.RpcContextAttachment;", "import org.apache.dubbo.rpc.RpcContext;")
                    .replace("org.apache.dubbo.rpc.RpcContextAttachment", "org.apache.dubbo.rpc.RpcContext")
                    .replace("getClientAttachment()", "getContext()")
                    .replace("getServerAttachment()", "getServerContext()");
            if (!content.equals(updated)) {
                Files.write(file, updated.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to rewrite " + file, e);
        }
    }
}
