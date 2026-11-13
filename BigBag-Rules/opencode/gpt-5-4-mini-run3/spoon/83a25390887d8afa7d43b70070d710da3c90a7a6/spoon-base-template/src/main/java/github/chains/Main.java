package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    private static final String MIN_KOTLIN_VERSION = "1.8.22";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: Main <project-root> [output-dir]");
            System.exit(1);
        }

        Path projectRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : projectRoot.resolve("spoon-out");

        rewriteKotlinVersionIfNeeded(projectRoot.resolve("pom.xml"));
        runSpoonTransformation(projectRoot, outputDir);
    }

    private static void runSpoonTransformation(Path projectRoot, Path outputDir) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.setSourceOutputDirectory(outputDir.toFile());

        addCandidates(launcher, projectRoot.resolve("src/main/java"));
        addCandidates(launcher, projectRoot.resolve("src/test/java"));
        addCandidates(launcher, projectRoot.resolve("src/main/kotlin"));
        addCandidates(launcher, projectRoot.resolve("src/test/kotlin"));

        launcher.addProcessor(new OkioCompatibilityProcessor());
        launcher.run();
    }

    private static void addCandidates(Launcher launcher, Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path) && isInterestingSourceFile(path))
                    .forEach(files::add);
        } catch (Exception ignored) {
            return;
        }

        for (Path file : files) {
            launcher.addInputResource(file.toString());
        }
    }

    private static boolean isInterestingSourceFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java") || fileName.endsWith(".kt");
    }

    private static void addIfPresent(Launcher launcher, Path path) {
        if (Files.isDirectory(path)) {
            launcher.addInputResource(path.toString());
        }
    }

    private static void rewriteKotlinVersionIfNeeded(Path pomPath) throws Exception {
        if (!Files.isRegularFile(pomPath)) {
            return;
        }

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomPath.toFile());
        document.getDocumentElement().normalize();

        if (!usesOkio3(document)) {
            return;
        }

        Element properties = firstChildElement(document.getDocumentElement(), "properties");
        if (properties == null) {
            return;
        }

        Element kotlinVersion = firstChildElement(properties, "kotlin.version");
        if (kotlinVersion == null) {
            return;
        }

        String currentVersion = kotlinVersion.getTextContent().trim();
        if (compareVersions(currentVersion, MIN_KOTLIN_VERSION) >= 0) {
            return;
        }

        kotlinVersion.setTextContent(MIN_KOTLIN_VERSION);

        var transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
    }

    private static boolean usesOkio3(Document document) {
        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Node node = dependencies.item(i);
            if (!(node instanceof Element dependency)) {
                continue;
            }
            String groupId = textOf(dependency, "groupId");
            String artifactId = textOf(dependency, "artifactId");
            String version = textOf(dependency, "version");
            if ("com.squareup.okio".equals(groupId)
                    && ("okio".equals(artifactId) || "okio-jvm".equals(artifactId))
                    && version.startsWith("3.")) {
                return true;
            }
        }
        return false;
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getElementsByTagName(name);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getParentNode() == parent && node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    private static String textOf(Element parent, String name) {
        Element child = firstChildElement(parent, name);
        return child == null ? "" : child.getTextContent().trim();
    }

    private static int compareVersions(String left, String right) {
        List<Integer> leftParts = parseVersion(left);
        List<Integer> rightParts = parseVersion(right);
        int max = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < max; i++) {
            int leftPart = i < leftParts.size() ? leftParts.get(i) : 0;
            int rightPart = i < rightParts.size() ? rightParts.get(i) : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static List<Integer> parseVersion(String version) {
        String[] parts = version.replaceAll("[^0-9.]", "").split("\\.");
        List<Integer> numbers = new java.util.ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                numbers.add(Integer.parseInt(part));
            }
        }
        return numbers;
    }

    private static class OkioCompatibilityProcessor extends AbstractProcessor<CtElement> {
        private static final Set<String> OKIO_PACKAGES = Set.of("okio", "com.squareup.okio");

        private static final Set<String> LONG_COUNT_METHODS = new HashSet<>(Set.of(
                "copyTo",
                "indexOf",
                "lastIndexOf",
                "rangeEquals",
                "read",
                "readByteArray",
                "readByteString",
                "readFully",
                "readString",
                "readUtf8",
                "readUtf8LineStrict",
                "request",
                "require",
                "skip",
                "write",
                "writeAll"
        ));

        @Override
        public void process(CtElement element) {
            if (element instanceof CtAbstractInvocation<?> invocation) {
                CtExecutableReference<?> executable = invocation.getExecutable();
                CtTypeReference<?> declaringType = executable == null ? null : executable.getDeclaringType();
                if (declaringType == null || !isOkioType(declaringType)) {
                    return;
                }
                if (!LONG_COUNT_METHODS.contains(executable.getSimpleName())) {
                    return;
                }
                rewriteIntegerLiterals(invocation, executable);
            }
        }

        private boolean isOkioType(CtTypeReference<?> type) {
            String qualifiedName = type.getQualifiedName();
            for (String prefix : OKIO_PACKAGES) {
                if (qualifiedName.equals(prefix) || qualifiedName.startsWith(prefix + ".")) {
                    return true;
                }
            }
            return false;
        }

        private void rewriteIntegerLiterals(CtAbstractInvocation<?> invocation, CtExecutableReference<?> executable) {
            List<CtExpression<?>> arguments = invocation.getArguments();
            List<CtTypeReference<?>> parameters = executable.getParameters();
            int limit = Math.min(arguments.size(), parameters.size());
            for (int i = 0; i < limit; i++) {
                CtTypeReference<?> parameterType = parameters.get(i);
                CtExpression<?> argument = arguments.get(i);
                if (!isLongType(parameterType) || !(argument instanceof CtLiteral<?> literal)) {
                    continue;
                }
                Object value = literal.getValue();
                if (value instanceof Integer integerValue) {
                    CtLiteral<Long> replacement = invocation.getFactory().Code().createLiteral(integerValue.longValue());
                    arguments.set(i, replacement);
                }
            }
        }

        private boolean isLongType(CtTypeReference<?> type) {
            return type != null && "long".equals(type.getSimpleName());
        }
    }
}
