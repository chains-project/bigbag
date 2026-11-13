package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

public class Main {

    private static final String LOGBACK_CLASSIC_GROUP_ID = "ch.qos.logback";
    private static final String LOGBACK_CLASSIC_ARTIFACT_ID = "logback-classic";
    private static final String LOGBACK_CORE_ARTIFACT_ID = "logback-core";
    private static final String SLF4J_GROUP_ID = "org.slf4j";
    private static final String SLF4J_API_ARTIFACT_ID = "slf4j-api";
    private static final String LOGBACK_CORE_VERSION = "1.4.8";
    private static final String TARGET_SLF4J_VERSION = "2.0.7";

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        Path sourceRoot = projectRoot.resolve("src");

        if (Files.isDirectory(sourceRoot)) {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceRoot.toString());
            launcher.getEnvironment().setNoClasspath(true);
            CtModel model = launcher.buildModel();
            if (model == null) {
                throw new IllegalStateException("Unable to build Spoon model for " + sourceRoot);
            }
        }

        for (Path pom : findPomFiles(projectRoot)) {
            updatePomIfNeeded(pom);
        }
    }

    private static Deque<Path> findPomFiles(Path root) throws Exception {
        Deque<Path> result = new ArrayDeque<>();
        Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().equals("pom.xml"))
                .forEach(result::add);
        return result;
    }

    private static void updatePomIfNeeded(Path pomPath) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        var document = factory.newDocumentBuilder().parse(pomPath.toFile());
        var dependencies = document.getElementsByTagName("dependency");

        boolean changed = false;
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = dependencies.item(i);
            String groupId = textOfChild(dependency, "groupId");
            String artifactId = textOfChild(dependency, "artifactId");
            String version = textOfChild(dependency, "version");

            if (LOGBACK_CLASSIC_GROUP_ID.equals(groupId) && LOGBACK_CLASSIC_ARTIFACT_ID.equals(artifactId) && version != null && version.startsWith("1.4.")) {
                changed |= updateVersion(document, dependency, SLF4J_GROUP_ID, SLF4J_API_ARTIFACT_ID, TARGET_SLF4J_VERSION);
                changed |= ensureDependencyVersion(document, LOGBACK_CLASSIC_GROUP_ID, LOGBACK_CORE_ARTIFACT_ID, LOGBACK_CORE_VERSION);
            }

            if (SLF4J_GROUP_ID.equals(groupId) && artifactId != null && artifactId.startsWith("slf4j-") && version != null && version.startsWith("1.7.")) {
                setChildText(dependency, "version", TARGET_SLF4J_VERSION);
                changed = true;
            }
        }

        if (changed) {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(pomPath.toFile()));
        }
    }

    private static boolean updateVersion(org.w3c.dom.Document document, org.w3c.dom.Node dependency,
                                        String groupId, String artifactId, String newVersion) {
        boolean changed = false;
        var children = dependency.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (groupId.equals(textOfChild(dependency, "groupId"))
                    && artifactId.equals(textOfChild(dependency, "artifactId"))
                    && "version".equals(child.getNodeName())) {
                child.setTextContent(newVersion);
                changed = true;
            }
        }
        return changed;
    }

    private static String textOfChild(org.w3c.dom.Node parent, String childName) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (childName.equals(child.getNodeName())) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    private static void setChildText(org.w3c.dom.Node parent, String childName, String value) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (childName.equals(child.getNodeName())) {
                child.setTextContent(value);
                return;
            }
        }
    }

    private static boolean ensureDependencyVersion(org.w3c.dom.Document document, String groupId, String artifactId, String newVersion) {
        var dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = dependencies.item(i);
            if (groupId.equals(textOfChild(dependency, "groupId")) && artifactId.equals(textOfChild(dependency, "artifactId"))) {
                String currentVersion = textOfChild(dependency, "version");
                if (!newVersion.equals(currentVersion)) {
                    setChildText(dependency, "version", newVersion);
                    return true;
                }
                return false;
            }
        }
        return false;
    }
}
