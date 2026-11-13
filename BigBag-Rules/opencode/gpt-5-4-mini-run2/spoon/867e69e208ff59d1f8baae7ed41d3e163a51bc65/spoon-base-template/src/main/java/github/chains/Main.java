package github.chains;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import spoon.Launcher;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

    static {
        TYPE_RENAMES.put(
                "org.apache.thrift.transport.TFramedTransport",
                "org.apache.thrift.transport.layered.TFramedTransport");
        TYPE_RENAMES.put(
                "org.apache.thrift.transport.TFastFramedTransport",
                "org.apache.thrift.transport.layered.TFastFramedTransport");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        File inputDir = new File(args[0]);
        if (!inputDir.isDirectory()) {
            throw new IllegalArgumentException("Input source directory does not exist: " + inputDir);
        }

        File outputDir = args.length > 1
                ? new File(args[1])
                : new File(inputDir.getParentFile(), inputDir.getName() + "-rewritten");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String newQualifiedName = TYPE_RENAMES.get(reference.getQualifiedName());
            if (newQualifiedName != null) {
                CtTypeReference<?> replacement = launcher.getFactory().Type().createReference(newQualifiedName);
                reference.replace(replacement);
            }
        }

        launcher.prettyprint();
    }
}
