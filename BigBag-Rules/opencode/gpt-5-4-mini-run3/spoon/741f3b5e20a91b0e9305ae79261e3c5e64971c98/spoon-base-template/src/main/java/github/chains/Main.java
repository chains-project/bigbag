package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory as the first argument");
        }

        Path sourceRoot = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Not a directory: " + sourceRoot);
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceRoot.toString());
        var model = launcher.buildModel();

        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (!isReadValueStringClass(invocation)) {
                continue;
            }
            String target = invocation.getTarget().toString();
            String json = invocation.getArguments().get(0).toString();
            String type = invocation.getArguments().get(1).toString();
            String replacement = target + ".readValue(new java.io.StringReader(" + json + "), " + type + ")";

            SourcePosition position = invocation.getPosition();
            if (position == null || !position.isValidPosition() || position.getCompilationUnit() == null) {
                continue;
            }

            Path file = Path.of(position.getCompilationUnit().getFile().getPath());
            String content = Files.readString(file);
            int start = position.getSourceStart();
            int end = position.getSourceEnd() + 1;
            content = content.substring(0, start) + replacement + content.substring(end);
            Files.writeString(file, content);
        }
    }

    private static boolean isReadValueStringClass(CtInvocation<?> invocation) {
        if (invocation.getExecutable() == null || invocation.getArguments().size() != 2) {
            return false;
        }
        if (!"readValue".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        if (invocation.getTarget() == null) {
            return false;
        }
        String signature = invocation.getExecutable().getSignature();
        return signature.contains("java.lang.String") && signature.contains("java.lang.Class");
    }
}
