package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.Comparator;
import java.util.stream.Stream;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_POINT_INDEX = "org.tinspin.index.PointIndex";
    private static final String NEW_POINT_MAP = "org.tinspin.index.PointMap";
    private static final String OLD_POINT_DISTANCE_FUNCTION = "org.tinspin.index.PointDistanceFunction";
    private static final String NEW_POINT_DISTANCE = "org.tinspin.index.PointDistance";
    private static final String OLD_POINT_ENTRY_DIST = "org.tinspin.index.PointEntryDist";
    private static final String NEW_POINT_ENTRY_KNN = "org.tinspin.index.Index.PointEntryKnn";
    private static final String OLD_KDTREE = "org.tinspin.index.kdtree.KDTree";

    private static final String[][] TEXT_REWRITES = new String[][] {
            {"import org.tinspin.index.PointIndex;", "import org.tinspin.index.PointMap;"},
            {"import org.tinspin.index.PointDistanceFunction;", "import org.tinspin.index.PointDistance;"},
            {"import org.tinspin.index.PointEntryDist;", "import org.tinspin.index.Index.PointEntryKnn;"},
            {"PointIndex<", "PointMap<"},
            {"PointDistanceFunction", "PointDistance"},
            {"PointEntryDist<", "PointEntryKnn<"},
            {"query1NN", "query1nn"}
    };

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <inputSourceDir> [outputDir] [classpathFile]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-spoon-out");
        String[] sourceClasspath = args.length > 2 ? readClasspath(Paths.get(args[2])) : new String[0];
        Path preparedInput = prepareInputCopy(input, output.resolveSibling(output.getFileName() + "-prepared"));

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.getEnvironment().setPrettyPrintingMode(Environment.PRETTY_PRINTING_MODE.FULLYQUALIFIED);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        if (sourceClasspath.length > 0) {
            launcher.getEnvironment().setSourceClasspath(sourceClasspath);
            launcher.getEnvironment().setNoClasspath(false);
        }
        launcher.addInputResource(preparedInput.toString());
        launcher.setSourceOutputDirectory(output.toFile());
        launcher.buildModel();

        CtModel model = launcher.getModel();
        transformTypeReferences(model);
        transformInvocations(model);

        launcher.prettyprint();
    }

    private static Path prepareInputCopy(Path input, Path preparedInput) {
        try {
            if (Files.exists(preparedInput)) {
                try (Stream<Path> walk = Files.walk(preparedInput)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                }
            }
            copyTree(input, preparedInput);
            try (Stream<Path> walk = Files.walk(preparedInput)) {
                walk.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java")).forEach(Main::rewriteJavaFile);
            }
            return preparedInput;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot prepare input copy", e);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            Path relative = source.relativize(path);
            Path dest = target.resolve(relative);
            try {
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void rewriteJavaFile(Path file) {
        try {
            String text = Files.readString(file);
            for (String[] rewrite : TEXT_REWRITES) {
                text = text.replace(rewrite[0], rewrite[1]);
            }
            text = rewriteKdTreeCreate(text);
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot rewrite " + file, e);
        }
    }

    private static String rewriteKdTreeCreate(String text) {
        String marker = "KDTree.create(";
        int index = 0;
        while ((index = text.indexOf(marker, index)) >= 0) {
            int argsStart = index + marker.length();
            int depth = 1;
            int commaIndex = -1;
            int i = argsStart;
            for (; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                } else if (c == ',' && depth == 1) {
                    commaIndex = i;
                    break;
                }
            }
            if (commaIndex < 0) {
                index = argsStart;
                continue;
            }
            int end = findCallEnd(text, commaIndex + 1);
            if (end < 0) {
                index = argsStart;
                continue;
            }
            text = text.substring(0, commaIndex) + ")" + text.substring(end + 1);
            index = index + marker.length();
        }
        return text;
    }

    private static int findCallEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int braceDepth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                braceDepth++;
                continue;
            }
            if (c == '}') {
                if (braceDepth > 0) {
                    braceDepth--;
                }
                continue;
            }
            if (braceDepth > 0) {
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    private static String[] readClasspath(Path classpathFile) {
        try {
            String content = Files.readString(classpathFile).trim();
            return content.isEmpty() ? new String[0] : content.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read classpath file: " + classpathFile, e);
        }
    }

    private static void transformTypeReferences(CtModel model) {
        for (CtTypeReference<?> ref : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            String qn = ref.getQualifiedName();
            if (OLD_POINT_INDEX.equals(qn)) {
                ref.replace(ref.getFactory().Type().createReference(NEW_POINT_MAP));
            } else if (OLD_POINT_DISTANCE_FUNCTION.equals(qn)) {
                ref.replace(ref.getFactory().Type().createReference(NEW_POINT_DISTANCE));
            } else if (OLD_POINT_ENTRY_DIST.equals(qn)) {
                ref.replace(ref.getFactory().Type().createReference(NEW_POINT_ENTRY_KNN));
            }
        }
    }

    private static void transformInvocations(CtModel model) {
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            if ("query1NN".equals(invocation.getExecutable().getSimpleName())) {
                invocation.getExecutable().setSimpleName("query1nn");
            }

            if (isKdTreeCreateWithMetric(invocation)) {
                while (invocation.getArguments().size() > 1) {
                    invocation.getArguments().remove(invocation.getArguments().size() - 1);
                }
            }
        }
    }

    private static boolean isKdTreeCreateWithMetric(CtInvocation<?> invocation) {
        if (!"create".equals(invocation.getExecutable().getSimpleName())) {
            return false;
        }
        CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
        if (declaringType == null || !OLD_KDTREE.equals(declaringType.getQualifiedName())) {
            return false;
        }
        return invocation.getArguments().size() == 2;
    }
}
