package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String OLD_PACKAGE = "javax.servlet";
    private static final String NEW_PACKAGE = "jakarta.servlet";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir>");
        }

        Path inputDir = Path.of(args[0]);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.addProcessor(new ServletNamespaceProcessor());
        launcher.buildModel();
        launcher.process();
        rewriteSourcesInPlace(inputDir);
    }

    private static void rewriteSourcesInPlace(Path inputDir) {
        try (Stream<Path> paths = Files.walk(inputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::rewriteFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite generated sources", e);
        }
    }

    private static void rewriteFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String updated = content
                    .replace("import javax.servlet.DispatcherType;\n", "")
                    .replace("import javax.servlet.FilterRegistration;\n", "")
                    .replace(OLD_PACKAGE, NEW_PACKAGE);
            if (!content.equals(updated)) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + file, e);
        }
    }

    private static final class ServletNamespaceProcessor extends AbstractProcessor<CtElement> {
        @Override
        public void process(CtElement element) {
            if (element instanceof CtTypeReference) {
                rewriteTypeReference((CtTypeReference<?>) element);
            }
        }

        private void rewriteTypeReference(CtTypeReference<?> typeReference) {
            String qualifiedName = typeReference.getQualifiedName();
            if (qualifiedName == null || !qualifiedName.startsWith(OLD_PACKAGE + ".")) {
                return;
            }

            String rewrittenName = NEW_PACKAGE + qualifiedName.substring(OLD_PACKAGE.length());
            typeReference.setPackage(getFactory().Package().createReference(NEW_PACKAGE));
            typeReference.setSimpleName(rewrittenName.substring(rewrittenName.lastIndexOf('.') + 1));
        }
    }
}
