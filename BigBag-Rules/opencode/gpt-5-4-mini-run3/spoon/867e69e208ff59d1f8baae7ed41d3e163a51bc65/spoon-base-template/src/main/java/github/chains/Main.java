package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final String OLD_TYPE = "org.apache.thrift.transport.TFramedTransport";
    private static final String NEW_TYPE = "org.apache.thrift.transport.layered.TFramedTransport";
    private static final String OLD_FAST_TYPE = "org.apache.thrift.transport.TFastFramedTransport";
    private static final String NEW_FAST_TYPE = "org.apache.thrift.transport.layered.TFastFramedTransport";
    private static final String OLD_IMPORT = "import " + OLD_TYPE + ";";
    private static final String OLD_FAST_IMPORT = "import " + OLD_FAST_TYPE + ";";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.addInputResource(input.toString());
        launcher.getEnvironment().setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        List<CtTypeReference<?>> references = launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference<?> reference : references) {
            String qualifiedName = reference.getQualifiedName();
            if (isOldTransportType(qualifiedName)) {
                reference.replace(createReplacementReference(launcher, qualifiedName));
            }
        }

        launcher.prettyprint();
        rewriteGeneratedSources(output);
    }

    private static boolean isOldTransportType(String qualifiedName) {
        return isOldType(qualifiedName, OLD_TYPE) || isOldType(qualifiedName, OLD_FAST_TYPE);
    }

    private static CtTypeReference<?> createReplacementReference(Launcher launcher, String qualifiedName) {
        String replacement = qualifiedName.startsWith(OLD_FAST_TYPE)
                ? qualifiedName.equals(OLD_FAST_TYPE) ? NEW_FAST_TYPE : NEW_FAST_TYPE + qualifiedName.substring(OLD_FAST_TYPE.length())
                : qualifiedName.equals(OLD_TYPE)
                    ? NEW_TYPE
                    : NEW_TYPE + qualifiedName.substring(OLD_TYPE.length());
        return launcher.getFactory().createReference(replacement);
    }

    private static boolean isOldType(String qualifiedName, String oldType) {
        return qualifiedName != null && (qualifiedName.equals(oldType) || qualifiedName.startsWith(oldType + "."));
    }

    private static void rewriteGeneratedSources(Path output) {
        try {
            Files.walk(output)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> replaceInFile(path, OLD_TYPE, NEW_TYPE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void replaceInFile(Path path, String oldText, String newText) {
        try {
            String content = Files.readString(path);
            String updated = content.replace(OLD_IMPORT + System.lineSeparator(), "")
                    .replace(OLD_IMPORT + "\n", "")
                    .replace(OLD_FAST_IMPORT + System.lineSeparator(), "")
                    .replace(OLD_FAST_IMPORT + "\n", "")
                    .replace(OLD_TYPE, NEW_TYPE)
                    .replace(OLD_FAST_TYPE, NEW_FAST_TYPE);
            if (!updated.equals(content)) {
                Files.writeString(path, updated);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
