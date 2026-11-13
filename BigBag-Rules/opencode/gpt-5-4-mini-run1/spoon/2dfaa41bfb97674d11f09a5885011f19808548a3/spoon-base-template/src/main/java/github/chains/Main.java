package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Main {
    private static final String OLD_METHOD = "getArgumentAt";
    private static final String NEW_METHOD = "getArgument";
    private static final String TARGET_TYPE = "org.mockito.invocation.InvocationOnMock";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.buildModel();

        Path outputRoot = Path.of(args[1]);
        Map<Path, StringBuilder> fileContents = new HashMap<>();
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!isTargetInvocation(invocation)) {
                continue;
            }

            SourcePosition position = invocation.getPosition();
            if (position == null || !position.isValidPosition() || position.getFile() == null) {
                continue;
            }

            Path file = outputRoot.resolve(relativizeInput(args[0], position.getFile()));
            StringBuilder content = fileContents.computeIfAbsent(file, Main::readFile);
            int start = position.getSourceStart();
            int end = position.getSourceEnd() + 1;
            String snippet = content.substring(start, end);
            int methodIndex = snippet.indexOf(OLD_METHOD);
            if (methodIndex < 0) {
                continue;
            }
            content.replace(start + methodIndex, start + methodIndex + OLD_METHOD.length(), NEW_METHOD);
        }

        for (Map.Entry<Path, StringBuilder> entry : fileContents.entrySet()) {
            Files.createDirectories(entry.getKey().getParent());
            Files.writeString(entry.getKey(), entry.getValue().toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean isTargetInvocation(CtInvocation<?> invocation) {
        if (!OLD_METHOD.equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        if (invocation.getArguments().size() != 2) {
            return false;
        }
        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        return declaringType == null || TARGET_TYPE.equals(declaringType.getQualifiedName());
    }

    private static Path relativizeInput(String inputRoot, File file) {
        return Path.of(inputRoot).toAbsolutePath().normalize().relativize(file.toPath().toAbsolutePath().normalize());
    }

    private static StringBuilder readFile(Path path) {
        try {
            return new StringBuilder(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
